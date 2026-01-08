package chatproject;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Date;

public class ChatAgent extends Agent {

    private JFrame myFrame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JComboBox<String> agentList;
    private String myName;
    
    // 📂 Cale dinamică pentru log-uri
    private String myLogFilePath; 

    @Override
    protected void setup() {
        myName = getLocalName();
        
        // 1. Configurare Sistem de Fișiere (Folder + Nume Unic)
        setupLoggingSystem();

        System.out.println("Agent " + myName + " started. Log file: " + myLogFilePath);

        // 2. Înregistrare în DF
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("chat-agent-service");
        sd.setName("JADE-chat");
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) {}

        // 3. Pornire GUI și Încărcare Istoric Specific
        SwingUtilities.invokeLater(() -> { 
            setupGui(); 
            loadHistoryFromFile(); 
        });

        // 4. Comportament Primire Mesaje
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    // Dacă primim semnal de închidere
                    if (msg.getContent().contains("SHUTDOWN_CMD")) { 
                        myAgent.doDelete(); 
                        return; 
                    }
                    
                    String sender = msg.getSender().getLocalName();
                    String content = msg.getContent();
                    
                    // Formatare mesaj pentru afișare și salvare
                    String logEntry;
                    if(sender.equals("vision") || sender.equals("summarizer") || sender.equals("oracle")) {
                        logEntry = "🤖 " + sender.toUpperCase() + ": " + content + "\n";
                    } else {
                        logEntry = "[MSG] " + sender + ": " + content + "\n";
                    }
                    
                    if (chatArea != null) chatArea.append(logEntry);
                    saveToFile(logEntry);
                } else { block(); }
            }
        });

        // 5. Actualizare listă agenți (Ticker)
        addBehaviour(new TickerBehaviour(this, 5000) {
            protected void onTick() { updateActiveAgents(); }
        });
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (Exception e) {}
        if (myFrame != null) myFrame.dispose();
        System.out.println("Agent " + myName + " shutting down.");
    }

    // --- 📂 LOGICĂ NOUĂ PENTRU LOGURI ---
    
    private void setupLoggingSystem() {
        // Creăm folderul "logs" dacă nu există
        File logDir = new File("logs");
        if (!logDir.exists()) {
            boolean created = logDir.mkdir();
            if(created) System.out.println("Created 'logs' directory.");
        }
        
        // Definim calea unică: logs/history_agent1.txt
        myLogFilePath = "logs/history_" + myName + ".txt";
    }

    private void saveToFile(String text) {
        // Scriem în fișierul specific agentului
        try (FileWriter fw = new FileWriter(myLogFilePath, true); 
             PrintWriter pw = new PrintWriter(fw)) {
             
            // Nu mai punem data aici dacă textul o are deja, sau o putem adăuga
            // Simplu: adăugăm timestamp doar la salvare
            pw.print(new Date() + " | " + text);
            
        } catch (IOException e) {
            System.err.println("Error saving log for " + myName + ": " + e.getMessage());
        }
    }
    
    private void loadHistoryFromFile() {
         File file = new File(myLogFilePath);
         if(file.exists()) {
             try(BufferedReader br = new BufferedReader(new FileReader(file))) {
                 String line;
                 chatArea.append("--- 📜 HISTORY RESTORED (" + myName + ") ---\n");
                 while((line = br.readLine()) != null) {
                     // Parsăm linia pentru a scoate timestamp-ul tehnic la afișare, dacă vrei
                     // Sau afișăm tot. Aici afișăm partea de după "|" pentru curățenie
                     if(line.contains("|")) {
                        chatArea.append(line.substring(line.indexOf("|") + 2) + "\n");
                     } else {
                        chatArea.append(line + "\n");
                     }
                 }
                 chatArea.append("--------------------------------------\n");
             } catch(Exception e){
                 e.printStackTrace();
             }
         } else {
             chatArea.append("--- New Session (No previous logs) ---\n");
         }
    }

    // --- GUI & BUTTONS --- (Aceleași ca înainte)
    
    private void setupGui() {
        myFrame = new JFrame("Proiect SI (Team A) - " + myName);
        myFrame.setSize(600, 500);
        myFrame.setLayout(new BorderLayout());

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        myFrame.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // ZONA DE SUS
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        
        JPanel selectionPanel = new JPanel(new BorderLayout());
        selectionPanel.add(new JLabel(" Chat Partner: "), BorderLayout.WEST);
        agentList = new JComboBox<>();
        selectionPanel.add(agentList, BorderLayout.CENTER);
        JButton killBtn = new JButton("☠ EXIT ALL");
        killBtn.setBackground(Color.RED);
        killBtn.setForeground(Color.WHITE);
        killBtn.addActionListener(e -> sendShutdownSignal());
        selectionPanel.add(killBtn, BorderLayout.EAST);
        
        JPanel toolPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton imgBtn = new JButton("📷 Upload Image (Vision)");
        JButton fileBtn = new JButton("📄 Upload File (Summary)");
        toolPanel.add(imgBtn);
        toolPanel.add(fileBtn);
        
        topPanel.add(selectionPanel);
        topPanel.add(toolPanel);
        myFrame.add(topPanel, BorderLayout.NORTH);

        // ZONA DE JOS
        JPanel botPanel = new JPanel(new BorderLayout());
        inputField = new JTextField();
        JPanel buttonGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton sendBtn = new JButton("Send Chat");
        JButton sumTextBtn = new JButton("📝 Summarize Input");
        sumTextBtn.setBackground(Color.ORANGE);

        buttonGroup.add(sumTextBtn);
        buttonGroup.add(sendBtn);
        
        botPanel.add(inputField, BorderLayout.CENTER);
        botPanel.add(buttonGroup, BorderLayout.EAST);
        myFrame.add(botPanel, BorderLayout.SOUTH);

        // ACTIUNI
        sendBtn.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        imgBtn.addActionListener(e -> handleImageUpload());
        fileBtn.addActionListener(e -> handleFileUpload());
        sumTextBtn.addActionListener(e -> handleTextSummary());

        myFrame.setVisible(true);
    }

    // --- FUNCȚII AUXILIARE ---

    private void sendMessage() {
        String content = inputField.getText();
        String recipient = (String) agentList.getSelectedItem();
        if (content.isEmpty() || recipient == null) return;
        
        ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
        msg.addReceiver(new AID(recipient, AID.ISLOCALNAME));
        msg.setContent(content);
        send(msg);
        
        // Logăm ce am trimis noi
        String log = "Me -> " + recipient + ": " + content + "\n";
        chatArea.append(log);
        saveToFile(log);
        inputField.setText("");
    }
    
    private void handleTextSummary() {
        String text = inputField.getText();
        if (text.isEmpty()) { JOptionPane.showMessageDialog(myFrame, "Type text first!"); return; }
        AID summ = findService("summarizer-service");
        if (summ == null) { JOptionPane.showMessageDialog(myFrame, "Summarizer offline!"); return; }
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(summ);
        msg.setContent(text);
        send(msg);
        chatArea.append("[SENT SUMMARY REQ] " + text.substring(0, Math.min(text.length(), 20)) + "...\n");
        inputField.setText("");
    }

    private void handleImageUpload() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(myFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                AID vision = findService("vision-service");
                if (vision == null) { JOptionPane.showMessageDialog(myFrame, "Vision offline!"); return; }
                byte[] content = Files.readAllBytes(file.toPath());
                String encoded = Base64.getEncoder().encodeToString(content);
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(vision);
                msg.setContent(encoded);
                send(msg);
                chatArea.append("[SENT IMAGE] " + file.getName() + "\n");
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private void handleFileUpload() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(myFrame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                AID summ = findService("summarizer-service");
                if (summ == null) { JOptionPane.showMessageDialog(myFrame, "Summarizer offline!"); return; }
                String content = new String(Files.readAllBytes(file.toPath()));
                ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
                msg.addReceiver(summ);
                msg.setContent(content);
                send(msg);
                chatArea.append("[SENT FILE] " + file.getName() + "\n");
            } catch (Exception ex) { ex.printStackTrace(); }
        }
    }

    private AID findService(String serviceType) {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType(serviceType);
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) return result[0].getName();
        } catch (FIPAException e) { e.printStackTrace(); }
        return null;
    }

    private void updateActiveAgents() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("chat-agent-service"); 
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (agentList == null) return;
            Object current = agentList.getSelectedItem();
            agentList.removeAllItems();
            for (DFAgentDescription ag : result) {
                if (!ag.getName().getLocalName().equals(myName)) 
                    agentList.addItem(ag.getName().getLocalName());
            }
            if (current != null) agentList.setSelectedItem(current);
        } catch (FIPAException e) {}
    }
    
    private void sendShutdownSignal() {
        String[] services = {"chat-agent-service", "vision-service", "summarizer-service"};
        ACLMessage killMsg = new ACLMessage(ACLMessage.INFORM);
        killMsg.setContent("SHUTDOWN_CMD");
        for(String s : services) {
             DFAgentDescription tmpl = new DFAgentDescription();
             ServiceDescription sd = new ServiceDescription();
             sd.setType(s);
             tmpl.addServices(sd);
             try {
                DFAgentDescription[] res = DFService.search(this, tmpl);
                for(DFAgentDescription ag : res) killMsg.addReceiver(ag.getName());
             } catch(Exception e){}
        }
        send(killMsg);
    }
}