package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 고객센터 문의 글
 */
@Entity
@Getter
@Table(name = "support")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "guest_id", nullable = false, length = 50)
    private String guestId;

    /** BCrypt 해시. 이 변경 이전에 쓴 글은 평문일 수 있다 - SupportService.verifyGuest 가 확인 성공 시 해시로 바꾼다. */
    @Column(name = "guest_password", nullable = false, length = 100)
    private String guestPassword;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public SupportEntity(String title, String content, String guestId, String guestPassword) {
        this.title = title;
        this.content = content;
        this.guestId = guestId;
        this.guestPassword = guestPassword;
    }

    /** 평문으로 저장돼 있던 예전 글의 비밀번호를 해시로 교체한다. */
    public void replacePasswordHash(String encodedPassword) {
        this.guestPassword = encodedPassword;
    }
}
