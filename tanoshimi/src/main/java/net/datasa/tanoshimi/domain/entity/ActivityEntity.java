package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
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
    @Column(name = "venue_type", nullable = false, length = 10)
    private VenueType venueType;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ActiveStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public boolean isFree() { return priceKrw == 0 && priceJpy == 0; }
}
