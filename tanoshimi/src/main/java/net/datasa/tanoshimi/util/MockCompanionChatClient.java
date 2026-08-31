package net.datasa.tanoshimi.util;

import java.util.List;
import net.datasa.tanoshimi.domain.dto.CompanionChatTurn;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * app.companion.api-key 없이도 위젯 데모가 바로 동작하도록 하는 더미 응답기.
 * 실제 API 연동 전까지 프론트엔드/캐릭터 애니메이션을 테스트할 때 쓴다.
 *
 * <p>주의: 이 클라이언트는 사용자가 뭐라고 하든 무시하고 미리 정해둔 문장 중 하나를
 * 그냥 랜덤으로 돌려준다 - 진짜 대화를 하는 게 아니다. "AI가 동문서답한다"는 건 대부분
 * provider 가 아직 이 mock 상태라서 그런 것이니, application-local.yml 에서
 * app.companion.provider 를 anthropic 또는 gemini 로 바꾸고 API 키를 넣어야 실제로
 * 똑똑한 대화 + 실시간 여행지 검색이 가능해진다(AnthropicChatClient/GeminiChatClient 참고).
 */
@Component
@ConditionalOnProperty(name = "app.companion.provider", havingValue = "mock", matchIfMissing = true)
public class MockCompanionChatClient implements CompanionChatClient {

    private static final String[] CANNED_REPLIES = {
            "오빠~ 나 지금은 데모 모드라서 이 몇 마디밖에 못 해 🙏 진짜 대화하려면 API 키 넣어줘야 돼!",
            "음~ 그건 나도 궁금한데! (지금은 mock 상태라 아무 질문에나 이 말만 반복하는 중이야)",
            "날씨랑 계획표는 실제 AI 연동해야 똑똑하게 도와줄 수 있어, 지금은 데모 응답이야 ✈️",
            "오사카 얘기하는 거야? (사실 지금은 무슨 말을 해도 똑같이 반응해 - mock 모드라서 그래)",
            "나 진짜 실력 발휘하려면 application-local.yml 에 provider: gemini 나 anthropic 넣어줘!",
    };

    @Override
    public String reply(List<CompanionChatTurn> history, String userMessage, String username) {
        int idx = Math.floorMod(userMessage.hashCode() + history.size(), CANNED_REPLIES.length);
        return CANNED_REPLIES[idx];
    }
}

