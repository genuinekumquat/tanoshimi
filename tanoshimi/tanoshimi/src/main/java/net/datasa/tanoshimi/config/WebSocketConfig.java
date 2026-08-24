package net.datasa.tanoshimi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 파티 채팅 + 계획표 실시간 동시편집이 공유하는 STOMP 웹소켓 인프라.
 *
 * <p>구독 채널:
 * <ul>
 *   <li>/topic/chat/{roomId}     — 채팅 메시지 브로드캐스트</li>
 *   <li>/topic/planner/{scheduleId} — 계획표 변경사항 브로드캐스트
 *       (동시편집 충돌 정책: "나중에 저장한 사람이 이긴다" 단순 규칙 - 팀 확정 방침,
 *       정교한 OT/CRDT 병합 로직은 도입하지 않는다)</li>
 * </ul>
 * 발행 prefix 는 /app (예: 클라이언트가 /app/chat.send/{roomId} 로 전송).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }
}
