package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 여행 게시판 글이자 마이페이지 피드.
 * 같은 엔티티/테이블을 두 화면(게시판, 마이페이지)이 서로 다른 조회 조건으로 보여줄 뿐이다.
 * (게시판 = 전체 조회, 마이페이지 = user_id 로 필터링된 조회 — 접근 경로만 다름)
 */
@Entity
@Getter
@Table(name = "posts")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PostEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_id")
    private PartyEntity party;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 50)
    private String region;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PostEntity(UserEntity user, PartyEntity party, String title, String content, String region, String thumbnailUrl) {
        this.user = user;
        this.party = party;
        this.title = title;
        this.content = content;
        this.region = region;
        this.thumbnailUrl = thumbnailUrl;
        this.likeCount = 0;
    }

    public void edit(String title, String content, String thumbnailUrl) {
        this.title = title;
        this.content = content;
        this.thumbnailUrl = thumbnailUrl;
    }

    public void increaseLike() { this.likeCount++; }
    public void decreaseLike() { if (this.likeCount > 0) this.likeCount--; }
}
