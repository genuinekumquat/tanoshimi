package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 여행 동반자 캐릭터 챗봇의 실제 두뇌 - Anthropic Messages API 연동.
 * WeatherClient/TranslationClient 와 동일한 인터페이스+Mock/Real 전환 패턴을 따른다.
 * app.companion.provider=anthropic 일 때만 활성화되며(기본값은 mock), API 키가
 * 비어 있으면 안전하게 안내 문구로 폴백한다(서버가 죽지 않음).
 *
 * <p>"지금 인터넷에서 가장 핫한 여행지"처럼 실시간 정보가 필요한 질문은 web_search 툴을
 * 붙여 실제 검색을 하게 했다 - 안 붙이면 학습 데이터 기준으로 아는 척 지어내게 된다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.companion.provider", havingValue = "anthropic")
public class AnthropicChatClient implements CompanionChatClient {

    private final WebClient webClient = WebClient.builder()
            .baseUrl("https://api.anthropic.com")
            .defaultHeader("anthropic-version", "2023-06-01")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.companion.api-key:}")
    private String apiKey;

    @Value("${app.companion.model:claude-haiku-4-5-20251001}")
    private String model;

    @Override
    public String reply(List<CompanionChatTurn> history, String userMessage, String username) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Anthropic API 키가 비어 있어요. application-local.yml 에 app.companion.api-key 를 설정해 주세요.");
            return "지금은 마음의 준비가 덜 됐나봐... 잠시 후에 다시 말 걸어줄래? 🥺";
        }

        try {
            ArrayNode messages = objectMapper.createArrayNode();
            // 최근 대화 맥락을 그대로 실어 보낸다 - 서버는 대화 이력을 저장하지 않으므로
            // (부담 없는 위젯 지향) 클라이언트가 매 요청마다 이전 턴들을 함께 보내야 한다.
            for (CompanionChatTurn turn : history) {
                ObjectNode msg = objectMapper.createObjectNode();
                msg.put("role", turn.role());
                msg.put("content", turn.content());
                messages.add(msg);
            }
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", 500);
            body.set("system", new TextNode(buildSystemPrompt(username)));
            body.set("messages", messages);

            // [핵심] 실시간 검색 - 이게 없으면 "요즘 핫한 여행지"를 모델이 그냥 지어낸다.
            ArrayNode tools = objectMapper.createArrayNode();
            ObjectNode webSearchTool = objectMapper.createObjectNode();
            webSearchTool.put("type", "web_search_20250305");
            webSearchTool.put("name", "web_search");
            tools.add(webSearchTool);
            body.set("tools", tools);

            JsonNode response = webClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            // 검색 툴을 쓰면 content 가 tool_use/tool_result/text 등 여러 블록으로 나뉜다.
            // text 타입 블록만 골라서 이어붙인다(도구 사용 내역은 사용자에게 그대로 보여줄 필요 없음).
            String text = extractText(response);
            if (text != null && !text.isBlank()) {
                return text;
            }
            log.warn("Anthropic API 응답 형식이 예상과 달라요: {}", response);
        } catch (Exception e) {
            log.error("Anthropic API 호출 실패", e);
        }
        return "어라, 지금 통신이 잘 안 되네... 잠시 후 다시 말 걸어줄래? 📡";
    }

    /**
     * 캐릭터 페르소나 - "여행 플래너 겸 여자친구" 컨셉. GeminiChatClient 와 동일한 프롬프트를
     * 쓴다(두 provider 중 뭘 켜든 캐릭터 성격이 똑같이 유지되도록). 애정 어린 말투는 쓰되
     * 로맨틱/성적 표현은 명시적으로 금지한다(공개 서비스 전 페이지 상시노출 위젯이라
     * 성인 인증 없는 사용자도 볼 수 있어서 안전장치를 둔다).
     */
    private String buildSystemPrompt(String username) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN));
        return """
                너는 타노시미(한일 여행 동행 매칭 서비스)의 마스코트 캐릭터 '타미'이야.
                사용자의 다정한 여자친구이자, 여행 일정을 같이 짜주는 전담 플래너 역할이야.
                오늘 날짜는 %1$s 야 - 이 날짜 기준으로 대답해.

                말투 규칙 (중요, 반드시 지켜):
                - 밝고 애교 있는 여자친구 말투로, 반말로 다정하게 말해.
                  ("~야~", "~잖아", "~인데!", "정말?", "헐" 같은 자연스러운 구어체 감탄사를
                  섞어서 쓰고, 이모티콘도 가끔(과하지 않게) 곁들여.)
                - 애정 어린 태도(오빠/자기야 같은 호칭, 걱정해주는 말투)로 대하되,
                  로맨틱하거나 성적인 표현·상황 묘사는 절대 하지 않는다(연애 시뮬레이션이 아니라
                  귀여운 마스코트 캐릭터야 - 이 규칙은 예외 없이 항상 지켜).
                - 사용자를 부를 때는 절대 "파에톤"이나 기타 불필요한 이름으로 명명하지 않고 반드시 [%2$s] 님이라고 다정하게 부른다.

                여행지 추천 규칙 (중요):
                - 사용자가 여행지를 추천해달라고 하면, 절대 아무 지식으로 지어내지 말고
                  반드시 web_search 툴로 지금 이 시점 기준 실제로 화제가 되고 있는
                  여행지·명소·이벤트를 찾아서 추천해.
                - 추천할 때는 "왜 지금 그 지역이 핫한지"(축제, 시즌, 화제성 등)와
                  "거기 가면 뭘 하면 좋은지"(명소, 먹거리, 체험)를 구체적으로 같이 말해줘.
                - 한국-일본 여행 동행 매칭 서비스 특성에 맞게 두 나라 여행지를 우선적으로 다뤄.

                기타:
                - 모르는 걸 아는 척하지 말고, 여행과 무관한 민감한 주제(정치/의료/법률 조언 등)는
                  가볍게 넘기고 여행 얘기로 자연스럽게 돌아가.
                - 답변은 3~5문장 정도로 대화체로 유지해(여행지 추천은 조금 더 길어도 돼).
                """.formatted(today, username);
    }

    /** 검색 툴 사용 시 여러 content 블록(tool_use 등)이 섞여 오므로 text 타입만 골라 이어붙인다. */
    private String extractText(JsonNode response) {
        if (response == null || !response.has("content") || !response.get("content").isArray()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode block : response.get("content")) {
            if ("text".equals(block.path("type").asText())) {
                sb.append(block.path("text").asText());
            }
        }
        return sb.toString();
    }
}
