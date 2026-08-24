package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.PreferredLang;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.translation.provider", havingValue = "deepl")
@RequiredArgsConstructor
public class DeepLTranslationClient implements TranslationClient {

    private final WebClient webClient = WebClient.builder().baseUrl("https://api-free.deepl.com/v2").build();

    @Value("${app.translation.api-key}")
    private String apiKey;

    @Override
    public String translate(String text, PreferredLang from, PreferredLang to) {
        if (text == null || text.isBlank() || from == to) {
            return text;
        }

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("DeepL API key is missing. Returning original text.");
            return text;
        }

        try {
            String targetLang = (to == PreferredLang.ja) ? "JA" : "KO";
            String sourceLang = (from == PreferredLang.ja) ? "JA" : "KO";

            JsonNode response = webClient.post()
                    .uri("/translate")
                    .header(HttpHeaders.AUTHORIZATION, "DeepL-Auth-Key " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "text", new String[]{text},
                            "source_lang", sourceLang,
                            "target_lang", targetLang
                    ))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("translations")) {
                return response.get("translations").get(0).get("text").asText();
            }
        } catch (Exception e) {
            log.error("Failed to translate text via DeepL", e);
        }

        return text;
    }
}
