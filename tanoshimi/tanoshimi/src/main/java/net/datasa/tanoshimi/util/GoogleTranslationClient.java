package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.PreferredLang;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "mock", matchIfMissing = true)
public class GoogleTranslationClient implements TranslationClient {

    private final WebClient webClient = WebClient.builder().baseUrl("https://translate.googleapis.com").build();

    @Override
    public String translate(String text, PreferredLang from, PreferredLang to) {
        if (from == to) return text;

        try {
            // Google Translate 비공식 API 호출 (로컬 개발용 무료 엔드포인트)
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/translate_a/single")
                            .queryParam("client", "gtx")
                            .queryParam("sl", from.name())
                            .queryParam("tl", to.name())
                            .queryParam("dt", "t")
                            .queryParam("q", text)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.isArray() && response.size() > 0) {
                JsonNode translations = response.get(0);
                StringBuilder translatedText = new StringBuilder();
                
                // 여러 문장으로 쪼개져서 올 경우 하나로 합침
                for (JsonNode node : translations) {
                    if (node.isArray() && node.size() > 0) {
                        translatedText.append(node.get(0).asText());
                    }
                }
                
                if (translatedText.length() > 0) {
                    return translatedText.toString();
                }
            }
        } catch (Exception e) {
            log.error("Failed to translate text via Google Translate API", e);
        }

        return "[번역 실패] " + text;
    }
}
