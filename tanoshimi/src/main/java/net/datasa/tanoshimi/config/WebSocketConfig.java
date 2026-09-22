package net.datasa.tanoshimi.config;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.websocket.WebSocketSubscriptionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
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
 *
 * <p>[TNSM-21] 인바운드 채널에 {@link WebSocketSubscriptionInterceptor} 를 걸어 위 두 topic을
 * 구독할 때마다 채팅방/파티 멤버십을 확인한다 - 그전까지는 로그인만 하면 누구나 어떤
 * roomId/scheduleId 든 구독해서 남의 채팅·계획표 변경을 실시간으로 엿볼 수 있었다.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketSubscriptionInterceptor subscriptionInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(subscriptionInterceptor);
    }
}
