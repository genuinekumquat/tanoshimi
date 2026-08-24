package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 신고 - 게시글/파티/사용자 공용.
 * target_type + target_id 로 무엇을 신고했는지 가리킨다(서로 다른 테이블을 가리킬 수
 * 있는 다형 연관이라 target_id 에는 FK 를 걸지 않는다). 신고 시점의 제목/이름을
 * targetLabel 에 스냅샷으로 남겨서, 관리자 화면에서 매번 조인 안 해도 뭘 신고했는지 바로 보인다.
 */
@Entity
@Getter
@Table(name = "reports")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reporter_id", nullable = false)
    private UserEntity reporter;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 10)
    private ReportTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "target_label", nullable = false, length = 200)
    private String targetLabel;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReportStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public ReportEntity(UserEntity reporter, ReportTargetType targetType, Long targetId, String targetLabel, String reason) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.reason = reason;
        this.status = ReportStatus.pending;
    }

    public void resolve() {
        this.status = ReportStatus.resolved;
        this.reviewedAt = LocalDateTime.now();
    }

    public void dismiss() {
        this.status = ReportStatus.dismissed;
        this.reviewedAt = LocalDateTime.now();
    }
}
