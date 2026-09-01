package net.datasa.tanoshimi.service;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.DmRoomView;
import net.datasa.tanoshimi.domain.entity.ChatMessageEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomMemberEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomType;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatMessageRepository;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 간(1:1) 실시간 채팅 — 목록/입장 검증 담당. 메시지 송수신 자체는 ChatWebSocketController(STOMP). */
@Service
@RequiredArgsConstructor
public class DmService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("MM.dd HH:mm");

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Transactional(readOnly = true)
    public List<DmRoomView> listMyRooms(UserEntity me) {
        return chatRoomMemberRepository.findByUser(me).stream()
                .map(ChatRoomMemberEntity::getRoom)
                .filter(room -> room.getType() == ChatRoomType.dm)
                .map(room -> toView(room, me))
                .sorted(Comparator.comparing(DmRoomView::lastMessageTime).reversed())
                .toList();
    }

    private DmRoomView toView(ChatRoomEntity room, UserEntity me) {
        Optional<ChatRoomMemberEntity> other = chatRoomMemberRepository.findOtherMember(room, me);
        String otherName = other.map(m -> m.getUser().getName()).orElse("알 수 없음");
        Long otherId = other.map(m -> m.getUser().getId()).orElse(null);

        Optional<ChatMessageEntity> lastMessage = chatMessageRepository.findTopByRoomOrderByCreatedAtDesc(room);
        String preview = lastMessage.map(ChatMessageEntity::getContent).orElse("대화를 시작해 보세요");
        String time = lastMessage.map(m -> m.getCreatedAt().format(TIME_FMT)).orElse("");

        return new DmRoomView(room.getId(), otherId, otherName, preview, time);
    }

    /** 파티방과 마찬가지로, 이 방의 멤버가 맞는지 서버에서 한 번 더 확인한다. */
    @Transactional(readOnly = true)
    public void assertMember(ChatRoomEntity room, UserEntity user) {
        if (!chatRoomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new BusinessException(ErrorCode.NOT_PARTY_MEMBER, "본인의 쪽지함만 볼 수 있습니다.");
        }
    }

    @Transactional(readOnly = true)
    public String otherUserName(ChatRoomEntity room, UserEntity me) {
        return chatRoomMemberRepository.findOtherMember(room, me)
                .map(m -> m.getUser().getName())
                .orElse("알 수 없음");
    }

    @Transactional(readOnly = true)
    public Long otherUserId(ChatRoomEntity room, UserEntity me) {
        return chatRoomMemberRepository.findOtherMember(room, me)
                .map(m -> m.getUser().getId())
                .orElse(null);
    }
}
