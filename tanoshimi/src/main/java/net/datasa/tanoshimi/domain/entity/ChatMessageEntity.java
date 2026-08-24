package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 채팅 메시지. 번역은 저장하지 않고 그때그때 클라이언트 요청으로 처리한다
 * (원문은 그대로 두고, 사용자가 번역 버튼을 누른 순간에만 TranslationClient 호출 - 휘발성).
 */
@Entity
@Getter
@Table(name = "chat_messages")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessageEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "room_id", nullable = false)
    private ChatRoomEntity room;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sender_id", nullable = false)
    private UserEntity sender;

    @Column(nullable = false, length = 1000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "original_lang", nullable = false, length = 5)
    private PreferredLang originalLang;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ChatMessageEntity(ChatRoomEntity room, UserEntity sender, String content, PreferredLang originalLang) {
        this.room = room; this.sender = sender; this.content = content; this.originalLang = originalLang;
    }
}
