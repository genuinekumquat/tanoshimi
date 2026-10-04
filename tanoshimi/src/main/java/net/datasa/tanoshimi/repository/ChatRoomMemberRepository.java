package net.datasa.tanoshimi.repository;

import net.datasa.tanoshimi.domain.entity.ChatRoomEntity;
import net.datasa.tanoshimi.domain.entity.ChatRoomMemberEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMemberEntity, Long> {
    Optional<ChatRoomMemberEntity> findByRoomAndUser(ChatRoomEntity room, UserEntity user);
    boolean existsByRoomAndUser(ChatRoomEntity room, UserEntity user);

    /** DM 목록/파티방 진입 시 room.type, room.party 를 바로 참조하므로 미리 JOIN FETCH 한다. */
    @Query("select crm from ChatRoomMemberEntity crm join fetch crm.room where crm.user = :user")
    List<ChatRoomMemberEntity> findByUser(@Param("user") UserEntity user);

    /** DM 방에서 "상대방"을 찾을 때 사용 - user 를 미리 JOIN FETCH. */
    @Query("select crm from ChatRoomMemberEntity crm join fetch crm.user where crm.room = :room and crm.user <> :me")
    Optional<ChatRoomMemberEntity> findOtherMember(@Param("room") ChatRoomEntity room, @Param("me") UserEntity me);
    
    
    /** [TNSM-72] 파티(채팅방) 삭제 시 먼저 지워야 하는 자식 테이블. */
    void deleteByRoom(ChatRoomEntity room);
}
