package com.dell.twin;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ChatController {

    private final GroqClient groqClient;
    private final ObjectMapper objectMapper;

    public ChatController() {
        this.groqClient = new GroqClient();
        this.objectMapper = new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/chat")
    public Map<String, Object> handleChat(@RequestBody Map<String, String> requestBody) {
        String userQuery = requestBody.get("message");
        if (userQuery == null || userQuery.isEmpty()) {
            return errorResponse("Message cannot be empty");
        }

        try {
            // ---- Router Logic ----
            String context = "";
            String simulationResult = "";
            String riskScore = "0";

            String lowerQuery = userQuery.toLowerCase();

            if (lowerQuery.contains("what if") || lowerQuery.contains("if")) {
                simulationResult = mockSimulateWhatIf(userQuery);
            } else if (lowerQuery.contains("predict") || lowerQuery.contains("fail") || lowerQuery.contains("risk")) {
                riskScore = mockPredictRisk(userQuery);
            } else {
                context = mockRetrieveContext(userQuery);
            }

            // ---- CHECK IF DUMMY KEY IS USED ----
            String aiReply;
            if (groqClient.isDummyKey()) {
                // Use mock response directly (skip API call)
                aiReply = generateMockReply(userQuery, context, simulationResult, riskScore);
            } else {
                // Build Prompt and call real Groq API
                String prompt = buildPrompt(userQuery, context, simulationResult, riskScore);
                String rawGroqResponse = groqClient.askGroq(prompt);
                aiReply = groqClient.parseGroqResponse(rawGroqResponse);
            }

            // Parse AI reply (which is JSON)
            Map<String, Object> aiJson = objectMapper.readValue(aiReply, Map.class);

            // Build Final Response
            Map<String, Object> finalResponse = new HashMap<>();
            finalResponse.put("success", true);
            finalResponse.put("diagnosis", aiJson.getOrDefault("diagnosis", "No diagnosis"));
            finalResponse.put("recommendation", aiJson.getOrDefault("recommendation", "No recommendation"));
            finalResponse.put("confidence", aiJson.getOrDefault("confidence", 0));
            finalResponse.put("evidence", context);
            finalResponse.put("simulation", simulationResult);
            finalResponse.put("risk", riskScore);

            return finalResponse;

        } catch (IOException e) {
            return errorResponse("Error: " + e.getMessage());
        } catch (Exception e) {
            return errorResponse("Unexpected error: " + e.getMessage());
        }
    }

    // ---- Generate Mock Reply (used when API key is dummy) ----
    private String generateMockReply(String query, String context, String sim, String risk) {
        String diagnosis = "Based on the telemetry, your laptop is experiencing high CPU load (87%) causing elevated temperatures.";
        String recommendation = "Close background applications and reduce CPU-intensive tasks.";
        int confidence = 92;

        if (query.toLowerCase().contains("battery")) {
            diagnosis = "Battery drain is accelerated due to high power consumption (15W) from CPU and GPU.";
            recommendation = "Lower screen brightness, disable Wi-Fi when not needed, and close unused apps.";
            confidence = 88;
        } else if (query.toLowerCase().contains("wifi") || query.toLowerCase().contains("network")) {
            diagnosis = "WiFi latency spikes correlate with high network traffic and CPU interrupts.";
            recommendation = "Check router placement or reduce simultaneous connections.";
            confidence = 75;
        } else if (query.toLowerCase().contains("fan")) {
            diagnosis = "Fan running at high speed (4500 RPM) due to temperature exceeding 85°C.";
            recommendation = "Clean air vents and ensure proper airflow.";
            confidence = 90;
        }

        if (sim != null && !sim.isEmpty()) {
            diagnosis += " Simulation shows: " + sim;
        }
        if (risk != null && !risk.isEmpty() && !risk.equals("0")) {
            recommendation += " Risk score: " + risk + "%.";
        }

        return String.format(
            "{\"diagnosis\":\"%s\",\"recommendation\":\"%s\",\"confidence\":%d}",
            diagnosis.replace("\"", "\\\""),
            recommendation.replace("\"", "\\\""),
            confidence
        );
    }

    // ---- Mocks ----
    private String mockRetrieveContext(String query) {
        return "At 2:15 PM, CPU was 90%, temperature was 85°C, fan ran at 5000 RPM.";
    }

    private String mockSimulateWhatIf(String query) {
        return "CPU +20% → Temp rises to 95°C, Fan at 6000 RPM.";
    }

    private String mockPredictRisk(String query) {
        return "75";
    }

    // ---- Helpers ----
    private String buildPrompt(String query, String context, String sim, String risk) {
        StringBuilder p = new StringBuilder();
        p.append("User: ").append(query).append("\n");
        if (!context.isEmpty()) p.append("Data: ").append(context).append("\n");
        if (!sim.isEmpty()) p.append("Sim: ").append(sim).append("\n");
        if (!risk.isEmpty()) p.append("Risk: ").append(risk).append("%\n");
        p.append("Reply as JSON: diagnosis, recommendation, confidence.");
        return p.toString();
    }

    private Map<String, Object> errorResponse(String msg) {
        Map<String, Object> err = new HashMap<>();
        err.put("success", false);
        err.put("error", msg);
        return err;
    }
}