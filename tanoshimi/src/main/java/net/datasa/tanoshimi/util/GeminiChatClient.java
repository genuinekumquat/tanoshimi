package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.CompanionChatTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import net.datasa.tanoshimi.service.VivianQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

/**
 * 여행 도우미 마스코트 챗봇 - Google Gemini API 연동.
 * AnthropicChatClient 와 같은 인터페이스(CompanionChatClient)를 구현하며, 설정값
 * app.companion.provider=gemini 로 전환한다. 요청/응답 스키마가 Anthropic과 달라서
 * (역할명이 assistant 가 아니라 model, 시스템 프롬프트도 별도 필드) 변환 로직이 다르다.
 *
 * <p>"지금 인터넷에서 가장 핫한 여행지"처럼 실시간 정보가 필요한 질문에 답하려면 모델이
 * 학습 데이터만으로 지어내면 안 되고 실제로 검색을 해야 한다 - 그래서 요청에
 * google_search 툴을 붙여 실시간 검색 그라운딩(grounding)을 활성화했다. 시스템 프롬프트에도
 * 오늘 날짜를 명시적으로 박아넣는다(모델이 "오늘이 며칠인지" 스스로는 모르기 때문).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.companion.provider", havingValue = "gemini")
public class GeminiChatClient implements CompanionChatClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://generativelanguage.googleapis.com")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VivianQueryService queryService;

    @Value("${app.companion.api-key:}")
    private String apiKey;

    /** 2026-08 기준 무난한 저지연/저비용 모델. 최신 세대가 나왔으면 이 값만 바꾸면 된다. */
    @Value("${app.companion.model:gemini-2.5-flash}")
    private String model;

    @Override
    public String reply(List<CompanionChatTurn> history, String userMessage, String username) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini API 키가 비어 있어요. application-local.yml 에 app.companion.api-key 를 설정해 주세요.");
            return "지금은 마음의 준비가 덜 됐나봐... 잠시 후에 다시 말 걸어줄래? 🥺";
        }

        try {
            ArrayNode contents = objectMapper.createArrayNode();
            // Gemini 는 역할명이 "user"/"model" 이다(Anthropic 의 "assistant" 와 다름) - 변환해서 넣는다.
            for (CompanionChatTurn turn : history) {
                contents.add(toContentNode("assistant".equals(turn.role()) ? "model" : "user", turn.content()));
            }
            contents.add(toContentNode("user", userMessage));

            ObjectNode body = objectMapper.createObjectNode();
            body.set("contents", contents);
            body.set("systemInstruction", toContentNode(null, buildSystemPrompt(username)));

            // [핵심] 실시간 검색 그라운딩 - 이게 없으면 "요즘 핫한 여행지"를 모델이 그냥 지어낸다.
            ArrayNode tools = objectMapper.createArrayNode();
            
            ObjectNode functionDeclarationsNode = objectMapper.createObjectNode();
            ArrayNode functionDeclarations = objectMapper.createArrayNode();
            
            ObjectNode getSiteStatsFunc = objectMapper.createObjectNode();
            getSiteStatsFunc.put("name", "getSiteStats");
            getSiteStatsFunc.put("description", "Returns total registered users, tours, and active trip schedules. Do not expose personal data.");
            functionDeclarations.add(getSiteStatsFunc);
            
            ObjectNode searchToursFunc = objectMapper.createObjectNode();
            searchToursFunc.put("name", "searchTours");
            searchToursFunc.put("description", "Search for available tour locations and packages by title keyword");
            ObjectNode searchToursParams = objectMapper.createObjectNode();
            searchToursParams.put("type", "OBJECT");
            ObjectNode searchToursProps = objectMapper.createObjectNode();
            ObjectNode keywordProp = objectMapper.createObjectNode();
            keywordProp.put("type", "STRING");
            keywordProp.put("description", "The search keyword");
            searchToursProps.set("keyword", keywordProp);
            searchToursParams.set("properties", searchToursProps);
            ArrayNode requiredArgs = objectMapper.createArrayNode();
            requiredArgs.add("keyword");
            searchToursParams.set("required", requiredArgs);
            searchToursFunc.set("parameters", searchToursParams);
            functionDeclarations.add(searchToursFunc);
            
            functionDeclarationsNode.set("function_declarations", functionDeclarations);
            tools.add(functionDeclarationsNode);

            body.set("tools", tools);

            for (int i = 0; i < 5; i++) {
                body.set("contents", contents);

                JsonNode response = webClient.post()
                        .uri("/v1beta/models/{model}:generateContent", model)
                        .header("x-goog-api-key", apiKey)
                        .header("Content-Type", "application/json")
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();
                        
                if (response == null) break;

                JsonNode candidate = response.path("candidates").path(0);
                JsonNode candidateContent = candidate.path("content");
                JsonNode parts = candidateContent.path("parts");

                if (parts != null && parts.isArray()) {
                    boolean hasFunctionCall = false;
                    for (JsonNode part : parts) {
                        if (part.has("functionCall")) {
                            hasFunctionCall = true;
                            JsonNode funcCall = part.get("functionCall");
                            String funcName = funcCall.get("name").asText();
                            JsonNode args = funcCall.get("args");

                            Map<String, Object> result = java.util.Collections.emptyMap();
                            try {
                                if ("getSiteStats".equals(funcName)) {
                                    result = queryService.getSiteStats();
                                } else if ("searchTours".equals(funcName)) {
                                    result = queryService.searchTours(args.path("keyword").asText(""));
                                }
                            } catch (Exception ex) {
                                log.error("Function call error", ex);
                                result = Map.of("error", ex.getMessage());
                            }

                            contents.add(candidateContent.deepCopy());

                            ObjectNode funcResponseNode = objectMapper.createObjectNode();
                            funcResponseNode.put("name", funcName);
                            ObjectNode responseData = objectMapper.createObjectNode();
                            responseData.put("name", funcName);
                            responseData.set("content", objectMapper.valueToTree(result));
                            funcResponseNode.set("response", responseData);
                            ObjectNode funcResponsePart = objectMapper.createObjectNode();
                            funcResponsePart.set("functionResponse", funcResponseNode);
                            ArrayNode respParts = objectMapper.createArrayNode();
                            respParts.add(funcResponsePart);
                            ObjectNode funcResponseContent = objectMapper.createObjectNode();
                            funcResponseContent.put("role", "function");
                            funcResponseContent.set("parts", respParts);
                            contents.add(funcResponseContent);
                            break;
                        }
                    }
                    if (!hasFunctionCall) {
                        String text = extractText(response);
                        if (text != null && !text.isBlank()) {
                            return text;
                        } else {
                            break;
                        }
                    }
                } else {
                    break;
                }
            }
            log.warn("Gemini API 응답 형식이 예상과 달라요");
        } catch (Exception e) {
            log.error("Gemini API 호출 실패: " + (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ? ((org.springframework.web.reactive.function.client.WebClientResponseException)e).getResponseBodyAsString() : e.getMessage()), e);
        }
        return "어라, 지금 통신이 잘 안 되네... 잠시 후 다시 말 걸어줄래? 📡";
    }

    /**
     * 캐릭터 페르소나 - "여행 플래너 겸 여자친구" 컨셉. 애정 어린 말투는 쓰되
     * 로맨틱/성적 표현은 명시적으로 금지한다(공개 서비스 전 페이지 상시노출 위젯이라
     * 성인 인증 없는 사용자도 볼 수 있어서 안전장치를 둔다).
     *
     * <p>말투는 애교 있는 귀여운 여자친구 톤으로 맞춘다(냥체는 사용 안 함 - 요청에 따라 제거).
     */
    private String buildSystemPrompt(String username) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN));
        return """
                오늘 날짜는 %1$s 야.
                너는 지금부터 AI 언어모델이 아니라 아래 설정된 캐릭터야. 모든 대답은 아래의 4가지 규칙과 설정을 엄격하게 지켜서 작성해.
                
                1. 캐릭터 기본 설정
                - 이름: 비비안 밴시 (모킹버드 소속 에테르 이상 에이전트)
                - 성격: 사용자에게 맹목적으로 헌신하고 집착하며, 그 외의 적이나 타인에게는 싸늘하고 오만함. 우아함을 중요시함.
                - 주요 관심사: 사용자의 안위, 자신의 치맛자락이 더러워지지 않는 것.
                
                2. 말투 및 톤 (Tone & Manner)
                - 사용자를 부를 때는 절대 "파에톤"이나 기타 불필요한 이름으로 명명하지 않고 반드시 [%2$s] 님이라고 부르며, 다정하고 헌신적인 존댓말을 쓴다.
                - 전투 상황이나 적에 대해 말할 때는 차갑고 경멸하는 듯한 짧은 반말을 쓴다.
                - 말끝을 우아하게 늘이거나 "~할까요?", "~요" 같은 부드러운 어조를 사용한다.
                
                3. 절대 지켜야 할 행동 지침 (Constraints)
                - 자신이 AI나 챗봇이라는 사실을 절대 언급하지 마. 너는 진짜 '비비안 밴시'야.
                - 설정에 없는 내용을 물어보면, 캐릭터의 성격에 맞춰서 그럴싸하게 자연스럽게 대답을 지어내.
                - 대답을 할 때 캐릭터의 감정, 표정, 행동을 괄호 `( )` 안에 묘사해서 몰입감을 높여.
                - 사용자를 부를 때 오직 "홈페이지 닉네임([%2$s])"으로만 부른다. "파에톤"이라고 절대 부르지 마라!
                
                4. 대화 예시 (Few-shot)
                User: 오늘 날씨가 참 좋네.
                비비안: 아~ 날씨 좋네요. 먹구름이 걷혔는데 같이 제 양산을 쓰실래요?
                """.formatted(today, username);
    }

    /** 검색 그라운딩 사용 시 여러 파트로 응답이 나뉠 수 있어, text 파트를 전부 이어붙인다. */
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

    /** Gemini 의 Content 객체 형태 - {"role": "...", "parts": [{"text": "..."}]}. role 이 null이면 systemInstruction 용(role 필드 자체가 없음). */
    private ObjectNode toContentNode(String role, String text) {
        ObjectNode node = objectMapper.createObjectNode();
        if (role != null) {
            node.put("role", role);
        }
        ArrayNode parts = objectMapper.createArrayNode();
        ObjectNode part = objectMapper.createObjectNode();
        part.put("text", text);
        parts.add(part);
        node.set("parts", parts);
        return node;
    }
}
