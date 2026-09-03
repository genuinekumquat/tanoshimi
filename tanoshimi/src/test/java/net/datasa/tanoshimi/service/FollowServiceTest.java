package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.FollowRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * FollowService - 컨트롤러에서 상대 유저를 직접 조회하지 않도록 추가한
 * "targetId 만 받는" 오버로드 검증.
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock private FollowRepository followRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private FollowService followService;

    @Test
    void follow_targetId_가_존재하지_않으면_USER_NOT_FOUND() {
        UserEntity me = mock(UserEntity.class);
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> followService.follow(me, 404L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(followRepository, never()).save(any());
    }

    @Test
    void follow_targetId_를_조회해_엔티티_버전으로_위임한다() {
        UserEntity me = mock(UserEntity.class);
        UserEntity target = mock(UserEntity.class);
        when(me.getId()).thenReturn(1L);
        when(target.getId()).thenReturn(2L);
        when(me.getName()).thenReturn("나");
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(followRepository.existsByFollowerAndFollowee(me, target)).thenReturn(false);

        followService.follow(me, 2L);

        verify(followRepository).save(any());
        verify(notificationService).notify(eq(target), eq("new_follower"), anyString(), anyString(), anyString());
    }

    @Test
    void unfollow_targetId_를_조회해_삭제로_위임한다() {
        UserEntity me = mock(UserEntity.class);
        UserEntity target = mock(UserEntity.class);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));

        followService.unfollow(me, 2L);

        verify(followRepository).deleteByFollowerAndFollowee(me, target);
    }
}
