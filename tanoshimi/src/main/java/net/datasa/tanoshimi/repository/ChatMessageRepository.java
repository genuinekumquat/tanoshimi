package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ChatMessageEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByRoomOrderByCreatedAtAsc(ChatRoomEntity room);

    /** DM 목록에서 마지막 메시지 미리보기용. */
    Optional<ChatMessageEntity> findTopByRoomOrderByCreatedAtDesc(ChatRoomEntity room);
    
    /** [TNSM-72] 파티(채팅방) 삭제 시 먼저 지워야 하는 자식 테이블. */
    void deleteByRoom(ChatRoomEntity room);
}
