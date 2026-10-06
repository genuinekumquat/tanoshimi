package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.PreferredLang;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Gemini 로 채팅 메시지 번역. app.translation.provider: gemini 일 때 활성화.
 * RealGeminiClient 와 달리 구글 검색 그라운딩(tools)을 붙이지 않는다 - 번역엔 필요 없고 429 원인이었음.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "gemini")
public class GeminiTranslationClient implements TranslationClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    @Value("${app.translation.api-key:}")
    private String apiKey;

    @Value("${app.translation.model:gemini-3.5-flash-lite}")
    private String model;

    @Override
    public String translate(String text, PreferredLang from, PreferredLang to) {
        if (text == null || text.isBlank() || from == to) return text;
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini translation API key not configured.");
            return "[번역 실패] " + text;
        }

        String prompt = "Translate the following chat message from " + langName(from) + " to " + langName(to)
                + ". Keep the casual chat tone, emoji and line breaks. Output ONLY the translation, no quotes or notes.\n\n"
                + text;

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.2)
        );

        try {
            JsonNode response = webClient.post()
                    .uri("/v1beta/models/{model}:generateContent", model)
                    .header("x-goog-api-key", apiKey)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String translated = response == null ? "" :
                    response.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").trim();
            if (!translated.isEmpty()) return translated;
            log.warn("Gemini 번역 응답이 비어 있음: {}", response);
        } catch (Exception e) {
            log.error("Failed to translate text via Gemini", e);
        }
        return "[번역 실패] " + text;
    }

    private static String langName(PreferredLang lang) {
        return lang == PreferredLang.ja ? "Japanese" : "Korean";
    }
}
