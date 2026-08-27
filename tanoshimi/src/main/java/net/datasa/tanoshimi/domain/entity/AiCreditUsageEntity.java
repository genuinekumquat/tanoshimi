package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * [v16 신규] 사용자별 일일 AI 크레딧 사용량. (user_id, usage_date) 조합당 1행이며,
 * 자정이 지나면 오늘 날짜의 새 행이 필요해질 뿐 기존 행을 초기화하지는 않는다(날짜별 이력이 남는 방식).
 * 전 사용자에게 동일한 daily_limit 이 지급된다(필드제약조건 확정 사항).
 */
@Entity
@Getter
@Table(name = "ai_credit_usage")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiCreditUsageEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit;

    public AiCreditUsageEntity(UserEntity user, LocalDate usageDate, int dailyLimit) {
        this.user = user;
        this.usageDate = usageDate;
        this.usedCount = 0;
        this.dailyLimit = dailyLimit;
    }

    public boolean hasRemaining() { return usedCount < dailyLimit; }
    public void consume() { this.usedCount += 1; }
    public int remaining() { return Math.max(0, dailyLimit - usedCount); }
}
