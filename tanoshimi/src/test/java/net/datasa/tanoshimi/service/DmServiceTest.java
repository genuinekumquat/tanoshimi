package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomMemberEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomType;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import net.datasa.tanoshimi.repository.ChatRoomRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DmService - DmChatController.openRoom / MessagesController 에서 옮겨온 로직 검증.
 */
@ExtendWith(MockitoExtension.class)
class DmServiceTest {

    @Mock private ChatRoomRepository chatRoomRepository;
    @Mock private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock private net.datasa.tanoshimi.repository.ChatMessageRepository chatMessageRepository;
    @Mock private UserRepository userRepository;
    @Mock private BlockService blockService;

    @InjectMocks
    private DmService dmService;

    private UserEntity me = mock(UserEntity.class);
    private UserEntity target = mock(UserEntity.class);

    // ---------------------------------------------------------------- openOrGetRoomWith

    @Test
    void openOrGetRoomWith_차단_관계면_BLOCKED_USER_이고_방을_만들지_않는다() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(blockService.isBlockedEitherWay(me, target)).thenReturn(true);

        assertThatThrownBy(() -> dmService.openOrGetRoomWith(me, 9L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCKED_USER);

        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void openOrGetRoomWith_이미_상대와의_DM방이_있으면_그_방_id_를_돌려주고_새로_만들지_않는다() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(blockService.isBlockedEitherWay(me, target)).thenReturn(false);

        ChatRoomEntity existing = mock(ChatRoomEntity.class);
        when(existing.getType()).thenReturn(ChatRoomType.dm);
        when(existing.getId()).thenReturn(7L);
        ChatRoomMemberEntity myMembership = mock(ChatRoomMemberEntity.class);
        when(myMembership.getRoom()).thenReturn(existing);
        when(chatRoomMemberRepository.findByUser(me)).thenReturn(List.of(myMembership));
        when(chatRoomMemberRepository.existsByRoomAndUser(existing, target)).thenReturn(true);

        assertThat(dmService.openOrGetRoomWith(me, 9L)).isEqualTo(7L);
        verify(chatRoomRepository, never()).save(any());
    }

    @Test
    void openOrGetRoomWith_기존_방이_없으면_새_방과_멤버_둘을_만든다() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(target));
        when(blockService.isBlockedEitherWay(me, target)).thenReturn(false);
        when(chatRoomMemberRepository.findByUser(me)).thenReturn(List.of());
        ChatRoomEntity created = mock(ChatRoomEntity.class);
        when(created.getId()).thenReturn(42L);
        when(chatRoomRepository.save(any(ChatRoomEntity.class))).thenReturn(created);

        assertThat(dmService.openOrGetRoomWith(me, 9L)).isEqualTo(42L);
        verify(chatRoomRepository).save(any(ChatRoomEntity.class));
        verify(chatRoomMemberRepository, times(2)).save(any(ChatRoomMemberEntity.class));
    }

    // ---------------------------------------------------------------- isOtherBlockedByMe

    @Test
    void isOtherBlockedByMe_상대를_특정할_수_없으면_false_이고_차단조회도_안_한다() {
        ChatRoomEntity room = mock(ChatRoomEntity.class);
        when(chatRoomMemberRepository.findOtherMember(room, me)).thenReturn(Optional.empty());

        assertThat(dmService.isOtherBlockedByMe(room, me)).isFalse();
        verify(blockService, never()).isBlockedByMe(any(), any());
    }

    @Test
    void isOtherBlockedByMe_상대가_있고_내가_차단했으면_true() {
        ChatRoomEntity room = mock(ChatRoomEntity.class);
        UserEntity other = mock(UserEntity.class);
        when(other.getId()).thenReturn(3L);
        ChatRoomMemberEntity otherMember = mock(ChatRoomMemberEntity.class);
        when(otherMember.getUser()).thenReturn(other);
        when(chatRoomMemberRepository.findOtherMember(room, me)).thenReturn(Optional.of(otherMember));
        when(userRepository.findById(3L)).thenReturn(Optional.of(other));
        when(blockService.isBlockedByMe(me, other)).thenReturn(true);

        assertThat(dmService.isOtherBlockedByMe(room, me)).isTrue();
    }
}
