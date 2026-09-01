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

    /**
     * [v19 신규] 이 스냅이 어느 "내 여행"(my_trips) 기록인지. 여행 횟수 집계와는 무관하다
     * (그건 my_trips 행 개수 자체가 근거 - MyTripService 참고) - 다만 [v19-4] 부터는
     * source=PARTY 인 여행이 실제로 "카운트"되려면(칭호/지도 반영) 이 trip 을 가리키는 스냅이
     * 최소 1장 있어야 한다(MyTripService.isCountable). 파티를 만들고 완료 처리만 해두고 실제로
     * 다녀오지 않아도 여행 기록이 남는 것을 막기 위함(2026-09-01 요청).
     */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id")
    private MyTripEntity trip;

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

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blinded = false;

    public void blind() { this.blinded = true; }

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PostEntity(UserEntity user, PartyEntity party, MyTripEntity trip, String title, String content, String region, String thumbnailUrl) {
        this.user = user;
        this.party = party;
        this.trip = trip;
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
