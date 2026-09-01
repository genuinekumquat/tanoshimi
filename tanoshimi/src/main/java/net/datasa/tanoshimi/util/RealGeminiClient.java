package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Primary
@Component
public class RealGeminiClient implements GeminiClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.companion.api-key:}")
    private String apiKey;

    @Value("${app.companion.model:gemini-flash-lite-latest}")
    private String model;

    @Override
    public String ask(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured.");
            return "UNKNOWN";
        }
        try {
            ArrayNode contents = objectMapper.createArrayNode();
            ObjectNode contentNode = objectMapper.createObjectNode();
            contentNode.put("role", "user");
            ArrayNode parts = objectMapper.createArrayNode();
            ObjectNode part = objectMapper.createObjectNode();
            part.put("text", prompt);
            parts.add(part);
            contentNode.set("parts", parts);
            contents.add(contentNode);

            ObjectNode body = objectMapper.createObjectNode();
            body.set("contents", contents);
            
            // Higher temperature for variance requested by user
            ObjectNode generationConfig = objectMapper.createObjectNode();
            generationConfig.put("temperature", 0.8);
            body.set("generationConfig", generationConfig);

            // Google Search Grounding to fetch internet tourist data
            ArrayNode tools = objectMapper.createArrayNode();
            ObjectNode googleSearchTool = objectMapper.createObjectNode();
            googleSearchTool.set("google_search", objectMapper.createObjectNode());
            tools.add(googleSearchTool);
            body.set("tools", tools);

            JsonNode response = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            return extractText(response);
        } catch (Exception e) {
            log.error("Gemini Real API call failed", e);
            return "UNKNOWN";
        }
    }

    private String extractText(JsonNode response) {
        if (response == null) return null;
        JsonNode parts = response.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray()) return null;
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.has("text")) {
                sb.append(part.get("text").asText());
            }
        }
        return sb.toString();
    }
}