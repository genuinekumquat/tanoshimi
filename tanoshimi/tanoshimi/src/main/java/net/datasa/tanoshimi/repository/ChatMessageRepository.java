package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ChatMessageEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {
    List<ChatMessageEntity> findByRoomOrderByCreatedAtAsc(ChatRoomEntity room);

    /** DM 목록에서 마지막 메시지 미리보기용. */
    Optional<ChatMessageEntity> findTopByRoomOrderByCreatedAtDesc(ChatRoomEntity room);
}
