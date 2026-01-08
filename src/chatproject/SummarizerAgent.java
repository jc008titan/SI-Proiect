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
import java.nio.charset.StandardCharsets;

public class SummarizerAgent extends Agent {
    
    // --- PASTE YOUR API KEY HERE ---
    private static final String API_KEY = "";
    
    @Override
    protected void setup() {
        System.out.println("📝 Summarizer Agent " + getLocalName() + " ready.");
        
        // 1. Register Service
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("summarizer-service"); 
        sd.setName("JADE-Summarizer");
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException e) { e.printStackTrace(); }

        // 2. Listen for requests
        addBehaviour(new CyclicBehaviour(this) {
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    if (msg.getContent().startsWith("SHUTDOWN")) { myAgent.doDelete(); return; }
                    
                    String textToSummarize = msg.getContent();
                    System.out.println("📝 Summarizing document (" + textToSummarize.length() + " chars)...");

                    String summary = callOpenAISummary(textToSummarize);
                    
                    ACLMessage reply = msg.createReply();
                    reply.setContent("[SUMMARY]: " + summary);
                    myAgent.send(reply);
                } else { block(); }
            }
        });
    }

    private String callOpenAISummary(String text) {
        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("Authorization", "Bearer " + API_KEY);
            con.setDoOutput(true);

            // --- ROBUST CLEANING ---
            String safeText = cleanTextForJson(text);

            String jsonInput = "{"
                    + "\"model\": \"gpt-4o-mini\","
                    + "\"messages\": [{"
                    + "  \"role\": \"system\", \"content\": \"You are a helpful assistant. Summarize this text.\"},"
                    + " {\"role\": \"user\", \"content\": \"" + safeText + "\"}"
                    + "]"
                    + "}";

            // Write Body
            try (OutputStream os = con.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            
            // Check Response Code
            int code = con.getResponseCode();
            if (code != 200) {
                // READ THE ERROR STREAM TO SEE WHY IT FAILED
                try(BufferedReader br = new BufferedReader(new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) errorResponse.append(line);
                    System.out.println("❌ OpenAI Error Payload: " + errorResponse.toString());
                }
                return "Error: API returned code " + code; 
            }

            // Read Success Response
            BufferedReader br = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);

            // Simple Parse
            String respStr = response.toString();
            int contentIndex = respStr.indexOf("\"content\": \"");
            if (contentIndex != -1) {
                int start = contentIndex + 12;
                int end = respStr.indexOf("\"", start);
                while (end != -1 && respStr.charAt(end - 1) == '\\') {
                    end = respStr.indexOf("\"", end + 1);
                }
                if (end != -1) {
                    String content = respStr.substring(start, end);
                    return content.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                }
            }
            return "Summary parsed incorrectly.";
            
        } catch (Exception e) { 
            e.printStackTrace();
            return "Error connecting to Summary API: " + e.getMessage(); 
        }
    }
    
    // --- HELPER TO PREVENT JSON ERRORS ---
    private String cleanTextForJson(String input) {
        if (input == null) return "";
        
        // 1. Truncate if too huge (OpenAI has limits, though gpt-4o-mini is generous)
        // A limit of 25,000 characters is safe and handles your Shoe.txt easily.
        if (input.length() > 25000) {
            input = input.substring(0, 25000) + "... [truncated]";
        }

        // 2. Escape Backslashes FIRST (This is usually what breaks JSON)
        input = input.replace("\\", "\\\\");
        
        // 3. Escape Quotes
        input = input.replace("\"", "\\\"");
        
        // 4. Handle Newlines and Tabs
        input = input.replace("\n", " ").replace("\r", "").replace("\t", " ");
        
        // 5. Remove non-printable control characters (0x00 to 0x1F)
        input = input.replaceAll("[\\x00-\\x1F]", "");
        
        return input;
    }
    
    @Override
    protected void takeDown() { try { DFService.deregister(this); } catch (Exception e){} }
}