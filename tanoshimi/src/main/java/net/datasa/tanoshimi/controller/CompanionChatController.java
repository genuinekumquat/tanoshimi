package net.datasa.tanoshimi.controller;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.CompanionChatRequest;
import net.datasa.tanoshimi.domain.dto.CompanionChatTurn;
import net.datasa.tanoshimi.util.CompanionChatClient;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전 페이지 우측에 붙는 여행 도우미 마스코트 위젯의 채팅 API.
 * 무상태(stateless) 설계 - 대화 이력을 DB에 저장하지 않고, 프론트엔드가 매 요청마다
 * 지금까지의 대화를 함께 실어 보낸다(localStorage 에 보관). 로그인 여부와 무관하게
 * 누구나 쓸 수 있는 가벼운 위젯이라 굳이 회원 데이터와 엮지 않았다.
 */
@RestController
@RequiredArgsConstructor
public class CompanionChatController {

    private static final int MAX_HISTORY_TURNS = 12; // 토큰 비용/응답속도를 위해 최근 대화만 유지

    private final CompanionChatClient companionChatClient;

    @PostMapping("/api/companion/chat")
    @ResponseBody
    public ApiResponse<String> chat(@RequestBody CompanionChatRequest request, @AuthenticationPrincipal CustomUserDetails principal) {
        if (request.message() == null || request.message().isBlank()) {
            return ApiResponse.fail("메시지를 입력해 주세요.");
        }
        if (request.message().length() > 500) {
            return ApiResponse.fail("메시지가 너무 길어요. 500자 이내로 보내주세요.");
        }

        List<CompanionChatTurn> history = request.history() == null ? List.of() : request.history();
        if (history.size() > MAX_HISTORY_TURNS) {
            history = history.subList(history.size() - MAX_HISTORY_TURNS, history.size());
        }

        String username = (principal != null) ? principal.getDisplayName() : "사용자";
        String reply = companionChatClient.reply(history, request.message().trim(), username);
        return ApiResponse.ok(reply);
    }
}
