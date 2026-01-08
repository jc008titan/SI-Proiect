package chatproject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class EnvLoader {

    public static String getApiKey() {
        String apiKey = null;
        try (BufferedReader br = new BufferedReader(new FileReader(".env"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("OPENAI_API_KEY=")) {
                    // Extragem valoarea de după semnul egal
                    apiKey = line.substring("OPENAI_API_KEY=".length()).trim();
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("❌ CRITICAL ERROR: Could not find .env file!");
            System.err.println("Please create a file named .env in the project root with OPENAI_API_KEY=sk-...");
        }

        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("❌ ERROR: API Key not found in .env file.");
            return null;
        }

        return apiKey;
    }
}