package chatproject;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import java.io.*;
import java.net.*;

public class GeneralAiAgent extends Agent {

    // --- PUNE CHEIA AICI ---
    private String API_KEY;
    private static final String MODEL = "gpt-4o-mini"; 

    @Override
    protected void setup() {
    	API_KEY = EnvLoader.getApiKey(); // Load key
        if (API_KEY == null) { doDelete(); return; }
        
        System.out.println("🔮 chatgpt Agent " + getLocalName() + " is online.");
        
        // 1. Îl înregistrăm ca "chat-agent-service" pentru a apărea în lista de contacte a tuturor!
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("chat-agent-service"); // Același tip ca oamenii, ca să apară în dropdown
        sd.setName("JADE-chatgpt");
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) {}

        // 2. Ascultă întrebări
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    if (msg.getContent().contains("SHUTDOWN")) { myAgent.doDelete(); return; }

                    String question = msg.getContent();
                    // Dacă mesajul e gol sau e o cerere ciudată, ignorăm
                    if(question == null || question.length() < 2) return;

                    System.out.println("🔮 chatgpt thinking about: " + question);
                    String answer = callOpenAI(question);

                    ACLMessage reply = msg.createReply();
                    reply.setContent("[chatgpt]: " + answer);
                    myAgent.send(reply);
                } else { block(); }
            }
        });
    }

    @Override
    protected void takeDown() { try { DFService.deregister(this); } catch (Exception e) {} }

    private String callOpenAI(String prompt) {
        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + API_KEY);
            con.setDoOutput(true);

            String safePrompt = prompt.replace("\"", "\\\"").replace("\n", " ");
            String jsonInput = "{\"model\": \"" + MODEL + "\", \"messages\": [{\"role\": \"user\", \"content\": \"" + safePrompt + "\"}]}";

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);

            String respStr = response.toString();
            int contentIndex = respStr.indexOf("\"content\": \"");
            if (contentIndex != -1) {
                int start = contentIndex + 12;
                int end = respStr.indexOf("\"", start);
                while (respStr.charAt(end - 1) == '\\') end = respStr.indexOf("\"", end + 1);
                return respStr.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
            }
            return "chatgpt is confused.";
        } catch (Exception e) { return "chatgpt is offline (API Error)."; }
    }
}