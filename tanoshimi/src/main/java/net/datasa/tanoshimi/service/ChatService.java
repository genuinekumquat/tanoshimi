package net.datasa.tanoshimi.service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ChatMessageView;
import net.datasa.tanoshimi.domain.entity.ChatMessageEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ChatMessageRepository;
import net.datasa.tanoshimi.repository.ChatRoomMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파티 채팅 / 개인간(DM) 채팅 공용 서비스.
 * 번역은 여기서 하지 않는다 - 원문을 그대로 저장하고, 번역은 TranslateController 가
 * 매 요청마다 TranslationClient 를 호출해서 휘발성으로 보여준다.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;

    /** 이 방의 멤버가 맞는지 확인 - 파티 전용 채팅방에 아무나 못 들어오게 막는 최종 방어선. */
    @Transactional(readOnly = true)
    public void assertMember(ChatRoomEntity room, UserEntity user) {
        if (!chatRoomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new BusinessException(ErrorCode.NOT_PARTY_MEMBER, "채팅방 참여자만 이용할 수 있습니다.");
        }
    }

    @Transactional
    public ChatMessageView send(ChatRoomEntity room, UserEntity sender, String content) {
        assertMember(room, sender);
        ChatMessageEntity saved = chatMessageRepository.save(
                new ChatMessageEntity(room, sender, content, sender.getPreferredLang()));
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageView> history(ChatRoomEntity room) {
        return chatMessageRepository.findByRoomOrderByCreatedAtAsc(room).stream()
                .map(this::toView)
                .toList();
    }

    private ChatMessageView toView(ChatMessageEntity m) {
        return new ChatMessageView(
                m.getId(), m.getRoom().getId(), m.getSender().getId(), m.getSender().getName(),
                m.getContent(), m.getOriginalLang().name(), m.getCreatedAt().format(TIME_FMT));
    }
}
