package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserBlockRepository;
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
 * BlockService - 컨트롤러에서 상대 유저를 직접 조회하지 않도록 추가한
 * "targetId 만 받는" 오버로드 검증(존재하지 않는 id 처리 + 위임).
 */
@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock private UserBlockRepository userBlockRepository;
    @Mock private FollowService followService;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private BlockService blockService;

    @Test
    void block_targetId_가_존재하지_않으면_USER_NOT_FOUND() {
        UserEntity me = mock(UserEntity.class);
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> blockService.block(me, 404L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void block_targetId_를_조회해_엔티티_버전으로_위임한다() {
        UserEntity me = mock(UserEntity.class);
        UserEntity target = mock(UserEntity.class);
        when(me.getId()).thenReturn(1L);
        when(target.getId()).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userBlockRepository.existsByBlockerAndBlocked(me, target)).thenReturn(false);

        blockService.block(me, 2L);

        verify(userBlockRepository).save(any());
        verify(followService).unfollow(me, target);
        verify(followService).unfollow(target, me);
    }
}
