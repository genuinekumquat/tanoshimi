package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 번개모임(파티). 모집 제한 조건(성별/연령/국적)을 충족하지 못하면
 * 신청 버튼 자체가 비활성화된다 (방장이 사람 보고 거르는 방식이 아님 - PartyEligibilityService 참고).
 */
@Entity
@Getter
@Table(name = "parties")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "owner_user_id", nullable = false)
    private UserEntity owner;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tour_id")
    private TourEntity tour;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(name = "departure_date", nullable = false)
    private LocalDate departureDate;

    /**
     * [v16 신규] 여행 일수. departure_date + durationDays = 종료일이며,
     * PartyCompletionScheduler 가 이 종료일 경과 여부로 파티를 자동 '완료' 전환한다.
     * 리뷰에서 지적된 누락 필드 - 기본값 1(당일치기).
     */
    @Column(name = "duration_days", nullable = false, columnDefinition = "tinyint default 1")
    private byte durationDays = 1;

    @Column(name = "budget_krw")
    private Integer budgetKrw;

    @Column(nullable = false)
    private byte capacity;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean blinded = false;

    public boolean isBlinded() { return blinded; }
    public void blind() { this.blinded = true; }

    @Column(name = "style_tag", length = 100)
    private String styleTag;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender_restriction", nullable = false, length = 12)
    private GenderRestriction genderRestriction;

    @Column(name = "age_min")
    private Byte ageMin;

    @Column(name = "age_max")
    private Byte ageMax;

    @Enumerated(EnumType.STRING)
    @Column(name = "nationality_restriction", nullable = false, length = 10)
    private NationalityRestriction nationalityRestriction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private PartyStatus status;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    public PartyEntity(UserEntity owner, TourEntity tour, String title, String description, String region,
                       LocalDate departureDate, byte durationDays, Integer budgetKrw, byte capacity, String styleTag,
                       GenderRestriction genderRestriction, Byte ageMin, Byte ageMax,
                       NationalityRestriction nationalityRestriction, String thumbnailUrl) {
        this.owner = owner;
        this.tour = tour;
        this.title = title;
        this.description = description;
        this.region = region;
        this.departureDate = departureDate;
        this.durationDays = durationDays <= 0 ? 1 : durationDays;
        this.budgetKrw = budgetKrw;
        this.capacity = capacity;
        this.styleTag = styleTag;
        this.genderRestriction = genderRestriction == null ? GenderRestriction.all : genderRestriction;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.nationalityRestriction = nationalityRestriction == null ? NationalityRestriction.all : nationalityRestriction;
        this.thumbnailUrl = thumbnailUrl;
        this.status = PartyStatus.recruiting;
    }

    public void markFull() { this.status = PartyStatus.full; }
    public void reopen() { this.status = PartyStatus.recruiting; }
    public void close() { this.status = PartyStatus.closed; }
    public void decideTour(TourEntity tour) { this.tour = tour; }

    /** [v16 신규] 여행 종료일 = departureDate + durationDays. 완료 자동처리 스케줄러의 판단 기준. */
    public LocalDate endDate() { return departureDate.plusDays(durationDays); }

    /** [v16 신규] PartyCompletionScheduler 전용 - 종료일이 지난 파티를 완료 상태로 전환한다. */
    public void markCompleted() { this.status = PartyStatus.completed; }

    public void changeThumbnail(String url) {
        this.thumbnailUrl = url;
    }

    public void updateInfo(TourEntity tour, String title, String description, String region,
                           LocalDate departureDate, byte durationDays, Integer budgetKrw, byte capacity, String styleTag,
                           GenderRestriction genderRestriction, Byte ageMin, Byte ageMax,
                           NationalityRestriction nationalityRestriction) {
        this.tour = tour;
        this.title = title;
        this.description = description;
        this.region = region;
        this.departureDate = departureDate;
        this.durationDays = durationDays <= 0 ? 1 : durationDays;
        this.budgetKrw = budgetKrw;
        this.capacity = capacity;
        this.styleTag = styleTag;
        this.genderRestriction = genderRestriction == null ? GenderRestriction.all : genderRestriction;
        this.ageMin = ageMin;
        this.ageMax = ageMax;
        this.nationalityRestriction = nationalityRestriction == null ? NationalityRestriction.all : nationalityRestriction;
    }
}
