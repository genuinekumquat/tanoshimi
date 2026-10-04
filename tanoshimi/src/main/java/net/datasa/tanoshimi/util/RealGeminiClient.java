package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;


@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.companion.provider", havingValue = "gemini")
public class RealGeminiClient implements GeminiClient {
    
    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // [TNSM-72 추가제안] 하루에 Gemini 호출이 성공/실패(429, 빈 응답, 기타 오류)로 각각
    // 몇 번씩 끝났는지 집계하기 위한 서비스. /admin/ai-stats 에서 확인할 수 있다.
    private final net.datasa.tanoshimi.service.AiCallStatsService aiCallStatsService;
    
    @Value("${app.companion.api-key:}")
    private String apiKey;
    
    @Value("${app.companion.model:gemini-3.5-flash-lite}")
    private String model;
    
    @Override
    public String ask(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API key not configured.");
            aiCallStatsService.record(net.datasa.tanoshimi.service.AiCallStatsService.Outcome.NO_API_KEY);
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
            // [TNSM-63] response_mime_type(JSON 강제)과 tools(구글 검색 그라운딩)를 같이 보내면
            // Gemini API가 candidates[0].content.parts 가 빈 응답을 돌려주는 경우가 있었다
            // (ai-validate 호출에서 NPE로 이어짐 - PlannerController.aiValidate 참고).
            // 구글 공식 문서상 responseMimeType(구조화 출력)과 tools는 함께 쓰는 게 권장되지 않는
            // 조합이라, JSON 강제는 빼고 프롬프트 지시문 + 아래 markdown 스트립으로만 JSON을 받는다.
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
            
            String text = extractText(response);
            if (text == null) {
                // [TNSM-63] 예외는 안 났지만 candidates/parts가 비어있는 경우(안전 필터, 빈 응답 등).
                // 원본 응답은 로그로만 남기고(디버깅용), 호출부(recommend/aiValidate)가 null을
                // 받아 그대로 NPE 나지 않도록 친절한 안내 JSON으로 대체한다.
                log.warn("Gemini 응답에 candidates/parts가 없음. 원본 응답: {}", response);
                aiCallStatsService.record(net.datasa.tanoshimi.service.AiCallStatsService.Outcome.EMPTY_RESPONSE);
                return "{\"briefing\": \"지금은 AI가 잠깐 쉬고 있어요. 잠시 후 다시 시도해 주세요.\", \"newSchedule\": []}";
            }
            aiCallStatsService.record(net.datasa.tanoshimi.service.AiCallStatsService.Outcome.SUCCESS);
            return text;
        } catch (Exception e) {
            log.error("Gemini Real API call failed", e);
            if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException we) {
                if (we.getStatusCode().value() == 429) {
                    aiCallStatsService.record(net.datasa.tanoshimi.service.AiCallStatsService.Outcome.RATE_LIMITED);
                    return "{\"briefing\": \"구글 할배가 화가 단단히 난 데스! (429 Rate Limit) 무료 API 한도를 초과해서 잠시 막힌 테치. 딱 1분만 숨 참고 다시 눌러보는 데스웅~\", \"newSchedule\": []}";
                }
            }
            // 원본 에러(키 만료·네트워크 등)는 로그에만 남기고, 사용자에게는 안내 + 빈 일정을 돌려준다.
            // newSchedule 이 비어 있으므로 aiValidate 는 일정을 바꾸지 않고 briefing 만 보여준다.
            aiCallStatsService.record(net.datasa.tanoshimi.service.AiCallStatsService.Outcome.OTHER_ERROR);
            return "{\"briefing\": \"지금은 AI가 잠깐 쉬고 있어요. 잠시 후 다시 시도해 주세요.\", \"newSchedule\": []}";
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