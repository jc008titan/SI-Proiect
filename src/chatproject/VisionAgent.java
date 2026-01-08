package chatproject;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class VisionAgent extends Agent {
    
    // PUNE CHEIA AICI
    private String API_KEY;

    protected void setup() {
    	API_KEY = EnvLoader.getApiKey(); // Load key
        if (API_KEY == null) { doDelete(); return; }

        System.out.println("👁️ Vision Agent " + getLocalName() + " ready.");
        
        // 1. Înregistrare serviciu "vision-service"
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("vision-service"); 
        sd.setName("JADE-Vision");
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) { e.printStackTrace(); }

        // 2. Ascultă cereri
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    if (msg.getContent().startsWith("SHUTDOWN")) { myAgent.doDelete(); return; }
                    
                    // Conținutul mesajului va fi Base64 Image String
                    String base64Image = msg.getContent();
                    String sender = msg.getSender().getLocalName();
                    System.out.println("👁️ Analyzing image for " + sender + "...");

                    String analysis = callOpenAIVision(base64Image);
                    
                    ACLMessage reply = msg.createReply();
                    reply.setContent("[VISION]: " + analysis);
                    myAgent.send(reply);
                } else { block(); }
            }
        });
    }

    // Apel special către GPT-4o pentru imagini
    private String callOpenAIVision(String base64Image) {
        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + API_KEY);
            con.setDoOutput(true);

            // JSON structurat pentru Vision
            String jsonInput = "{"
                    + "\"model\": \"gpt-4o-mini\"," 
                    + "\"messages\": [{"
                    + "  \"role\": \"user\","
                    + "  \"content\": ["
                    + "    {\"type\": \"text\", \"text\": \"Describe this image in detail.\"},"
                    + "    {\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/jpeg;base64," + base64Image + "\"}}"
                    + "  ]"
                    + "}]"
                    + "}";

            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), "utf-8"));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);

            // Quick parsing
            String respStr = response.toString();
            int contentIndex = respStr.indexOf("\"content\": \"");
            if (contentIndex != -1) {
                int start = contentIndex + 12;
                int end = respStr.indexOf("\"", start);
                while (respStr.charAt(end - 1) == '\\') end = respStr.indexOf("\"", end + 1);
                return respStr.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
            }
            return "Vision analysis failed.";
        } catch (Exception e) { return "Error connecting to Vision API."; }
    }
    
    protected void takeDown() { try { DFService.deregister(this); } catch (Exception e){} }
}