package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 패키지 대금의 인원별 분할 결제. 혼합 국적 파티는 각자 자기 나라 통화로 자기 몫만 결제한다. */
@Entity
@Getter
@Table(name = "reservation_payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationPaymentEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id", nullable = false)
    private ReservationEntity reservation;

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

    public ReservationPaymentEntity(ReservationEntity reservation, UserEntity user, Currency currency, int amount) {
        this.reservation = reservation;
        this.user = user;
        this.currency = currency;
        this.amount = amount;
        this.status = PaymentStatus.ready;
    }

    public void markPaid() { this.status = PaymentStatus.paid; this.paidAt = LocalDateTime.now(); }
    public void markFailed() { this.status = PaymentStatus.failed; }
}
