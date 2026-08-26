package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [v16 신규] 매너온도 가산/감산 이력(감사 로그).
 * 신고 3회 누적 도달 판정처럼 "지금까지 몇 번 어떤 사유로 바뀌었는지"를 근거로 삼아야 하는
 * 규칙의 데이터 소스이며, 정책·계산·트리거는 MannerTempService 가 단독 소유한다.
 */
@Entity
@Getter
@Table(name = "manner_temp_logs")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MannerTempLogEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    /** +0.5 / +0.3 / -1.0 / -0.5 등. */
    @Column(nullable = false, precision = 3, scale = 1)
    private BigDecimal delta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MannerTempReason reason;

    /** 관련 party_id 또는 report_id (다형 참조, FK 없음 - reports 테이블과 동일한 설계 원칙). */
    @Column(name = "related_id")
    private Long relatedId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MannerTempLogEntity(UserEntity user, BigDecimal delta, MannerTempReason reason, Long relatedId) {
        this.user = user;
        this.delta = delta;
        this.reason = reason;
        this.relatedId = relatedId;
    }
}
