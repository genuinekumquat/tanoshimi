package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 파티 채팅 / 개인간(DM) 채팅이 같은 구조를 공유한다. */
@Entity
@Getter
@Table(name = "chat_rooms")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ChatRoomType type;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_id")
    private PartyEntity party;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private ChatRoomEntity(ChatRoomType type, PartyEntity party) {
        this.type = type; this.party = party;
    }

    public static ChatRoomEntity forParty(PartyEntity party) {
        return new ChatRoomEntity(ChatRoomType.party, party);
    }

    public static ChatRoomEntity forDm() {
        return new ChatRoomEntity(ChatRoomType.dm, null);
    }
}
