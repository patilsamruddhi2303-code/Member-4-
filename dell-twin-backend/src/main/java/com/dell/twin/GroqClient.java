package com.dell.twin;

import okhttp3.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class GroqClient {

    private static final String GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama3-70b-8192";

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GroqClient() {
        this.client = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
        Dotenv dotenv = Dotenv.load();
        String key = dotenv.get("GROQ_API_KEY");
        if (key == null || key.isEmpty()) {
            System.err.println("WARNING: GROQ_API_KEY not found. Using dummy key. AI calls will fail.");
            key = "dummy_key_for_testing";
        }
        this.apiKey = key;
    }

    // ***** NEW METHOD ADDED HERE *****
    public boolean isDummyKey() {
        return this.apiKey.equals("dummy_key_for_testing");
    }
    // ***** END OF NEW METHOD *****

    @SuppressWarnings("unchecked")
    public String askGroq(String userPrompt) throws IOException {
        String systemPrompt = "You are a Dell system engineer. Reply in JSON with keys: diagnosis, recommendation, confidence.";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", MODEL);
        requestBody.put("temperature", 0.3);
        requestBody.put("max_tokens", 300);

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", systemPrompt);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userPrompt);

        Object[] messages = {systemMessage, userMessage};
        requestBody.put("messages", messages);

        String jsonBody = objectMapper.writeValueAsString(requestBody);

        Request request = new Request.Builder()
                .url(GROQ_API_URL)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected response code: " + response.code());
            }
            return response.body().string();
        }
    }

    @SuppressWarnings("unchecked")
    public String parseGroqResponse(String jsonResponse) throws IOException {
        Map<String, Object> responseMap = objectMapper.readValue(jsonResponse, Map.class);
        List<Map<String, Object>> choices = (List<Map<String, Object>>) responseMap.get("choices");
        if (choices != null && !choices.isEmpty()) {
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            return (String) message.get("content");
        }
        return "{\"diagnosis\":\"No response\",\"recommendation\":\"Try again\",\"confidence\":0}";
    }
}