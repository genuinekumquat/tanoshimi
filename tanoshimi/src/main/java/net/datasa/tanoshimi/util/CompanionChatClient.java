package net.datasa.tanoshimi.util;

import java.util.List;
import net.datasa.tanoshimi.domain.dto.CompanionChatTurn;

/**
 * 여행 도우미 마스코트 캐릭터 챗봇 인터페이스.
 * WeatherClient/TranslationClient 와 동일한 설계 원칙 - Mock(기본값)과 실제 AI
 * 연동(AnthropicChatClient)을 설정값 하나(app.companion.provider)로 전환한다.
 */
public interface CompanionChatClient {
    /**
     * @param history 지금까지의 대화 이력(서버는 저장하지 않음 - 클라이언트가 매번 함께 전달)
     * @param userMessage 이번에 사용자가 보낸 메시지
     * @param username 사용자의 닉네임
     */
    String reply(List<CompanionChatTurn> history, String userMessage, String username);
}
