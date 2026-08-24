package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 항공+숙박+교통 패키지 (고정가 더미데이터).
 * dep_time/arr_time/checkin/checkout 은 실시간 항공 데이터가 아니라
 * 계획표에서 이동/체크인 블록을 자동 배치하기 위한 "계산 기준점"이다.
 */
@Entity
@Getter
@Table(name = "tours")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)

public class TourEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 50)
    private String region;

    @Column(length = 50)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "price_krw", nullable = false)
    private int priceKrw;

    @Column(name = "price_jpy", nullable = false)
    private int priceJpy;

    @Column(name = "duration_nights", nullable = false)
    private byte durationNights;

    @Column(name = "dep_time")
    private LocalTime depTime;

    @Column(name = "arr_time")
    private LocalTime arrTime;

    @Column(name = "checkin_time", nullable = false)
    private LocalTime checkinTime;

    @Column(name = "checkout_time", nullable = false)
    private LocalTime checkoutTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "venue_type", nullable = false, length = 10)
    private VenueType venueType;

    @Column(name = "style_tag", length = 100)
    private String styleTag;

    @Column(name = "companion_recommend", length = 50)
    private String companionRecommend;

    @Column(name = "min_participants", nullable = false)
    private byte minParticipants;

    @Column(name = "max_participants", nullable = false)
    private byte maxParticipants;

    @Column(name = "includes_summary", columnDefinition = "TEXT")
    private String includesSummary;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "location_address", length = 300)
    private String locationAddress;

    private BigDecimal latitude;
    private BigDecimal longitude;

    @Column(name = "external_flight_url", length = 500)
    private String externalFlightUrl;

    @Column(name = "external_hotel_url", length = 500)
    private String externalHotelUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActiveStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

        @lombok.Builder
    public TourEntity(String title, String region, String category, String description, int priceKrw, int priceJpy, byte durationNights, LocalTime depTime, LocalTime arrTime, LocalTime checkinTime, LocalTime checkoutTime, VenueType venueType, String styleTag, String companionRecommend, byte minParticipants, byte maxParticipants, String includesSummary, String thumbnailUrl, String locationAddress, BigDecimal latitude, BigDecimal longitude, String externalFlightUrl, String externalHotelUrl, ActiveStatus status) {
        this.title = title; this.region = region; this.category = category; this.description = description; this.priceKrw = priceKrw; this.priceJpy = priceJpy; this.durationNights = durationNights; this.depTime = depTime; this.arrTime = arrTime; this.checkinTime = checkinTime; this.checkoutTime = checkoutTime; this.venueType = venueType; this.styleTag = styleTag; this.companionRecommend = companionRecommend; this.minParticipants = minParticipants; this.maxParticipants = maxParticipants; this.includesSummary = includesSummary; this.thumbnailUrl = thumbnailUrl; this.locationAddress = locationAddress; this.latitude = latitude; this.longitude = longitude; this.externalFlightUrl = externalFlightUrl; this.externalHotelUrl = externalHotelUrl; this.status = status;
    }

    public boolean isActive() { return status == ActiveStatus.active; }
}
