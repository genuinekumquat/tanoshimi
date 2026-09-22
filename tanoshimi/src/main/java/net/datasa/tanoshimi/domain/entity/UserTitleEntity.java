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
@Table(name = "user_titles")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTitleEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "title_id", nullable = false)
    private TitleEntity title;

    @CreatedDate
    @Column(name = "earned_at", nullable = false, updatable = false)
    private LocalDateTime earnedAt;

    /** 대표 칭호로 장착했는지. [TNSM-20] 한 유저당 최대 하나만 true - TitleService.equipTitle 이 보장한다. */
    @Column(name = "equipped", nullable = false)
    private boolean equipped = false;

    public UserTitleEntity(UserEntity user, TitleEntity title) {
        this.user = user; this.title = title;
    }

    public void equip() { this.equipped = true; }
    public void unequip() { this.equipped = false; }
}
