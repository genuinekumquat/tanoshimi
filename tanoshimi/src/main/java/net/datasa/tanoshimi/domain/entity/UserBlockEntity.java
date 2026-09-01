package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Getter
@Table(name = "user_blocks")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserBlockEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "blocker_id", nullable = false)
    private UserEntity blocker;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "blocked_id", nullable = false)
    private UserEntity blocked;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public UserBlockEntity(UserEntity blocker, UserEntity blocked) {
        this.blocker = blocker; this.blocked = blocked;
    }
}
