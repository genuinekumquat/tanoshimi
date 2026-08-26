package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 계획표에 드래그해서 넣는 사이트 제공 유료 액티비티. */
@Entity
@Getter
@Table(name = "activities")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 50)
    private String region;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", length = 10)
    private VenueType venueType;  // [v16] 판정 전 NULL 허용(기존엔 not-null이었음) - AI가 처음 조회될 때 판정 후 채움

    @Column(name = "style_tag", length = 100)
    private String styleTag;

    @Column(name = "duration_min", nullable = false)
    private int durationMin;

    @Column(name = "price_krw", nullable = false)
    private int priceKrw;

    @Column(name = "price_jpy", nullable = false)
    private int priceJpy;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    private BigDecimal latitude;
    private BigDecimal longitude;

    /** [v16 신규] 장소검색 API의 장소 고유 id - 같은 장소를 중복 저장하지 않기 위한 키. */
    @Column(name = "external_place_id", length = 200, unique = true)
    private String externalPlaceId;

    /** [v16 신규] 어느 API로 조회했는지(제공사 미확정 - 값 후보만 정의). */
    @Enumerated(EnumType.STRING)
    @Column(name = "place_provider", length = 10)
    private PlaceProvider placeProvider;

    /** [v16 신규] venue_type 캐싱 갱신 시각. */
    @Column(name = "cached_at")
    private LocalDateTime cachedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActiveStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isFree() { return priceKrw == 0 && priceJpy == 0; }

    /**
     * [v16 신규] 장소검색 API 결과로 새 액티비티(캐시 엔트리)를 만들 때 쓰는 생성자.
     * venueType 은 아직 판정 전이라 null 로 시작하고, cacheVenueType() 이 나중에 채운다.
     */
    @Builder
    public ActivityEntity(String title, String region, String styleTag, int durationMin,
                          int priceKrw, int priceJpy, String description, String thumbnailUrl,
                          BigDecimal latitude, BigDecimal longitude,
                          String externalPlaceId, PlaceProvider placeProvider) {
        this.title = title;
        this.region = region;
        this.styleTag = styleTag;
        this.durationMin = durationMin <= 0 ? 60 : durationMin;
        this.priceKrw = priceKrw;
        this.priceJpy = priceJpy;
        this.description = description;
        this.thumbnailUrl = thumbnailUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.externalPlaceId = externalPlaceId;
        this.placeProvider = placeProvider;
        this.status = ActiveStatus.active;
    }

    /**
     * [v16 신규] AI가 실내/실외/혼합을 처음 판정한 결과를 캐싱한다. 이후 조회는 이 캐시값을
     * 재사용하고 재판정하지 않는다(필드제약조건 확정 사항) - ChatbotActivityService 참고.
     */
    public void cacheVenueType(VenueType venueType) {
        this.venueType = venueType;
        this.cachedAt = LocalDateTime.now();
    }

    public boolean needsVenueTypeJudgement() { return venueType == null; }
}
