package net.datasa.tanoshimi.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.CompanionChatRequest;
import net.datasa.tanoshimi.domain.dto.CompanionChatTurn;
import net.datasa.tanoshimi.service.CompanionChatRateLimiter;
import net.datasa.tanoshimi.util.CompanionChatClient;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 여행 도우미 마스코트(타미) 위젯의 채팅 API.
 * 무상태(stateless) 설계 - 대화 이력을 DB에 저장하지 않고, 프론트엔드가 매 요청마다
 * 지금까지의 대화를 함께 실어 보낸다(localStorage 에 보관).
 *
 * <p>호출마다 외부 AI 요금이 나가므로 로그인 사용자만 쓸 수 있고(SecurityConfig), 사용자별 호출
 * 횟수를 제한한다(CompanionChatRateLimiter). 위젯은 계획표(로그인 필요) 화면에만 있어서
 * 비로그인 허용을 없애도 화면 동작은 그대로다. 프론트가 보내는 이력도 그대로 믿지 않고
 * 역할·길이를 걸러서 AI 로 넘긴다.
 */
@RestController
@RequiredArgsConstructor
public class CompanionChatController {

    private static final int MAX_HISTORY_TURNS = 12; // 토큰 비용/응답속도를 위해 최근 대화만 유지
    private static final int MAX_MESSAGE_LENGTH = 500;
    private static final int MAX_HISTORY_TURN_LENGTH = 3000; // 타미의 긴 답변도 들어갈 만큼만

    private final CompanionChatClient companionChatClient;
    private final CompanionChatRateLimiter rateLimiter;

    @PostMapping("/api/companion/chat")
    @ResponseBody
    public ApiResponse<String> chat(@RequestBody CompanionChatRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        if (request.message() == null || request.message().isBlank()) {
            return ApiResponse.fail("메시지를 입력해 주세요.");
        }
        if (request.message().length() > MAX_MESSAGE_LENGTH) {
            return ApiResponse.fail("메시지가 너무 길어요. 500자 이내로 보내주세요.");
        }

        rateLimiter.acquire(principal.getId());

        String reply = companionChatClient.reply(sanitizeHistory(request.history()), request.message().trim(),
                principal.getDisplayName());
        return ApiResponse.ok(reply);
    }

    /** 최근 대화만, 역할이 user/assistant 이고 내용이 있는 턴만, 턴마다 길이를 잘라서 넘긴다. */
    static List<CompanionChatTurn> sanitizeHistory(List<CompanionChatTurn> history) {
        if (history == null) {
            return List.of();
        }
        List<CompanionChatTurn> valid = history.stream()
                .filter(turn -> turn != null && turn.content() != null && !turn.content().isBlank())
                .filter(turn -> "user".equals(turn.role()) || "assistant".equals(turn.role()))
                .map(turn -> turn.content().length() <= MAX_HISTORY_TURN_LENGTH ? turn
                        : new CompanionChatTurn(turn.role(), turn.content().substring(0, MAX_HISTORY_TURN_LENGTH)))
                .toList();
        return valid.size() <= MAX_HISTORY_TURNS ? valid : valid.subList(valid.size() - MAX_HISTORY_TURNS, valid.size());
    }
}
