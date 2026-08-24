package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 계획표 '제출' 버튼을 누른 시점에 생성되는 액티비티 대금의 인원별 결제 의무. */
@Entity
@Getter
@Table(name = "trip_schedule_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripSchedulePaymentEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_id", nullable = false)
    private TripScheduleEntity schedule;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 5)
    private Currency currency;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public TripSchedulePaymentEntity(TripScheduleEntity schedule, UserEntity user, Currency currency, int amount) {
        this.schedule = schedule;
        this.user = user;
        this.currency = currency;
        this.amount = amount;
        this.status = PaymentStatus.ready;
    }

    public void markPaid() { this.status = PaymentStatus.paid; this.paidAt = LocalDateTime.now(); }
    public void markFailed() { this.status = PaymentStatus.failed; }
}
