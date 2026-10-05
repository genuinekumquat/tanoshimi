package net.datasa.tanoshimi.websocket;

import java.security.Principal;
import java.util.Optional;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketSubscriptionInterceptorTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private TripScheduleRepository tripScheduleRepository;
    @Mock private PartyMemberRepository partyMemberRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private WebSocketSubscriptionInterceptor interceptor;

    private Message<byte[]> subscribe(String destination, long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        Principal principal = () -> String.valueOf(userId);
        accessor.setUser(principal);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void givenUser(long id) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
    }

    @Test
    void 내_알림_채널은_구독할_수_있다() {
        givenUser(3L);
        Message<byte[]> message = subscribe("/topic/user.3.notifications", 3L);

        assertThat(interceptor.preSend(message, null)).isSameAs(message);
    }

    @Test
    void 남의_알림_채널은_구독할_수_없다() {
        givenUser(3L);

        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/user.4.notifications", 3L), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 알_수_없는_채널은_기본_거부() {
        assertThatThrownBy(() -> interceptor.preSend(subscribe("/topic/anything", 3L), null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }
}
