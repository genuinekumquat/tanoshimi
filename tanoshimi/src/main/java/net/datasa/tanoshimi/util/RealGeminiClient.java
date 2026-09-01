package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.companion.provider", havingValue = "gemini")
public class RealGeminiClient implements GeminiClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.companion.api-key:}")
    private String apiKey;

    @Value("${app.companion.model:gemini-3.5-flash-lite}")
    private String model;

    @Override
    public String ask(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured.");
            return "{\"briefing\": \"Gemini API key not configured.\", \"newSchedule\": []}";
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
            generationConfig.put("response_mime_type", "application/json");
            body.set("generationConfig", generationConfig);

            // Google Search Grounding to fetch internet tourist data
            ArrayNode tools = objectMapper.createArrayNode();
            ObjectNode googleSearchTool = objectMapper.createObjectNode();
            googleSearchTool.set("googleSearch", objectMapper.createObjectNode());
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
            String errMsg = e.getMessage().replace("\"", "'");
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException) {
                org.springframework.web.reactive.function.client.WebClientResponseException we = (org.springframework.web.reactive.function.client.WebClientResponseException) e;
                if (we.getStatusCode().value() == 429) {
                    return "{\"briefing\": \"구글 할배가 화가 단단히 난 데스! (429 Rate Limit) 무료 API 한도를 초과해서 잠시 막힌 테치. 딱 1분만 숨 참고 다시 눌러보는 데스웅~\", \"newSchedule\": []}";
                }
                errMsg = we.getResponseBodyAsString().replace("\"", "'").replace("\n", " ");
            }
            return "{\"briefing\": \"API 오류: " + errMsg + "\", \"newSchedule\": []}";
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


