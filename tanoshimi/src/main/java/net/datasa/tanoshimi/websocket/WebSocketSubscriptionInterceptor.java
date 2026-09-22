package net.datasa.tanoshimi.websocket;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.TripScheduleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * [TNSM-21] 채팅·계획표가 공유하는 STOMP 브로커(WebSocketConfig)의 구독(SUBSCRIBE) 시점에
 * 채팅방/파티 멤버십을 확인한다.
 *
 * <p>enableSimpleBroker("/topic") 은 기본적으로 로그인만 되어 있으면 destination 문자열에
 * 아무 제약 없이 구독을 허용한다. 메시지 발행(SEND) 쪽은 ChatWebSocketController가
 * ChatService.assertMember 로, PlannerController의 편집 API들이 lock/멤버십 확인으로 막혀
 * 있었지만, 구독 자체는 아무도 막지 않고 있었다 - roomId/scheduleId 숫자만 알면(연속된 ID라
 * 추측도 쉽다) 소속되지 않은 채팅방의 대화나 다른 파티의 계획표 변경을 실시간으로 몰래 읽을
 * 수 있었다(TNSM-21 "웹소켓 공용 인프라 점검"에서 발견).
 *
 * <p><b>[실제 구동 QA에서 발견]</b> 처음에는 ChatService/PartyService/TripPlannerService를
 * 직접 주입해서 assertMember 를 재사용했는데, 그러면 애플리케이션이 아예 기동하지 않는다:
 * 이 인터셉터는 WebSocketConfig 가 생성하고, ChatService → BlockService → FollowService →
 * NotificationService 를 타고 들어가면 NotificationService 가 SimpMessagingTemplate(=STOMP
 * 브로커, WebSocketConfig 가 구성)을 필요로 해서 WebSocketConfig ↔ 인터셉터가 서로를
 * 기다리는 순환 참조가 된다(컴파일은 되지만 Spring 컨테이너 기동이 실패 - 유닛 컴파일만
 * 해서는 못 잡고 실제로 띄워봐야 나온다). 그래서 서비스 대신 리포지토리를 직접 써서
 * ChatService/PartyService 의 의존성 그래프 자체를 타지 않게 끊었다 - 판정 로직은
 * ChatService.assertMember/PartyService.assertMember 와 동일하다.
 */
@Component
@RequiredArgsConstructor
public class WebSocketSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d+)$");
    private static final Pattern PLANNER_TOPIC = Pattern.compile("^/topic/planner/(\\d+)$");

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }

        Matcher chatMatcher = CHAT_TOPIC.matcher(destination);
        if (chatMatcher.matches()) {
            ChatRoomEntity room = chatRoomRepository.findById(Long.valueOf(chatMatcher.group(1)))
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_PARTY_MEMBER, "채팅방을 찾을 수 없습니다."));
            UserEntity user = resolveUser(accessor);
            if (!chatRoomMemberRepository.existsByRoomAndUser(room, user)) {
                throw new BusinessException(ErrorCode.NOT_PARTY_MEMBER, "채팅방 참여자만 이용할 수 있습니다.");
            }
            return message;
        }

        Matcher plannerMatcher = PLANNER_TOPIC.matcher(destination);
        if (plannerMatcher.matches()) {
            // party 를 곧장 참조하므로 findWithContextById(LEFT JOIN FETCH)로 가져온다 -
            // 일반 findById 로는 지연 로딩이라 트랜잭션 밖에서 LazyInitializationException.
            TripScheduleEntity schedule = tripScheduleRepository.findWithContextById(Long.valueOf(plannerMatcher.group(1)))
                    .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
            if (schedule.getParty() != null
                    && !partyMemberRepository.existsByPartyAndUser(schedule.getParty(), resolveUser(accessor))) {
                throw new BusinessException(ErrorCode.NOT_PARTY_MEMBER);
            }
            return message;
        }

        return message;
    }

    /** ChatWebSocketController.send() 와 같은 방식으로 STOMP 세션의 Principal 에서 유저를 찾는다. */
    private UserEntity resolveUser(StompHeaderAccessor accessor) {
        Principal principal = accessor.getUser();
        if (principal == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "로그인 세션이 없습니다.");
        }
        Long userId;
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            userId = userDetails.getId();
        } else {
            try {
                userId = Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND, "사용자 인증 정보 형식이 올바르지 않습니다.");
            }
        }
        return userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
