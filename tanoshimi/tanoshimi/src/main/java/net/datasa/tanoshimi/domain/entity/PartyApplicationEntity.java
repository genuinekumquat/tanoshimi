package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 파티 참가 신청.
 * 자격 요건(성별/연령/국적)은 신청 전에 PartyEligibilityService 가 이미 걸러내므로
 * 여기 도달했다는 것 자체가 자격 조건을 통과했다는 뜻이다. 방장은 message 만 보고 승인/거절한다.
 */
@Entity
@Getter
@Table(name = "party_applications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartyApplicationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_id", nullable = false)
    private PartyEntity party;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "applicant_id", nullable = false)
    private UserEntity applicant;

    @Column(length = 300)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ApplicationStatus status;

    @CreatedDate
    @Column(name = "applied_at", nullable = false, updatable = false)
    private LocalDateTime appliedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    public PartyApplicationEntity(PartyEntity party, UserEntity applicant, String message) {
        this.party = party;
        this.applicant = applicant;
        this.message = message;
        this.status = ApplicationStatus.pending;
    }

    public void approve() { this.status = ApplicationStatus.approved; this.reviewedAt = LocalDateTime.now(); }
    public void reject() { this.status = ApplicationStatus.rejected; this.reviewedAt = LocalDateTime.now(); }
}
