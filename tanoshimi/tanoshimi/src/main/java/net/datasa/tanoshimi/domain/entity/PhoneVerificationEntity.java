package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 알리고 SMS 인증. 인증번호는 해시로만 저장(평문 저장 금지). */
@Entity
@Getter
@Table(name = "phone_verifications")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhoneVerificationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VerificationPurpose purpose;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private PhoneVerificationEntity(String phone, String codeHash, VerificationPurpose purpose, LocalDateTime expiresAt) {
        this.phone = phone; this.codeHash = codeHash; this.purpose = purpose; this.expiresAt = expiresAt;
        this.attemptCount = 0;
    }

    public static PhoneVerificationEntity issue(String phone, String codeHash, VerificationPurpose purpose, LocalDateTime expiresAt) {
        return new PhoneVerificationEntity(phone, codeHash, purpose, expiresAt);
    }

    public boolean isExpired(LocalDateTime now) { return now.isAfter(expiresAt); }
    public boolean isVerified() { return verifiedAt != null; }
    public boolean isUsed() { return usedAt != null; }
    public void increaseAttempt() { this.attemptCount++; }
    public boolean isAttemptExceeded(int max) { return attemptCount >= max; }
    public void markVerified(LocalDateTime now) { this.verifiedAt = now; }
    public void markUsed(LocalDateTime now) { this.usedAt = now; }
}
