package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 패키지(tours) 예약. 결제 3단계 흐름의 1단계 - "항공+숙박 패키지 먼저 결제".
 * weatherAck: AI 챗봇이 날씨 비추천을 했는데도 사용자가 감수하고 예약을 진행한 경우 true.
 */
@Entity
@Getter
@Table(name = "reservations")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_id")
    private PartyEntity party;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "booked_by_user_id", nullable = false)
    private UserEntity bookedBy;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "tour_id", nullable = false)
    private TourEntity tour;

    @Column(name = "people_count", nullable = false)
    private byte peopleCount;

    @Column(name = "reservation_number", nullable = false, unique = true, length = 30)
    private String reservationNumber;

    @Column(name = "departure_date", nullable = false)
    private java.time.LocalDate departureDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReservationStatus status;

    @Column(name = "weather_ack", nullable = false)
    private boolean weatherAck;

    @Column(name = "weather_ack_note", length = 200)
    private String weatherAckNote;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Builder
    public ReservationEntity(PartyEntity party, UserEntity bookedBy, TourEntity tour, byte peopleCount,
                             String reservationNumber, java.time.LocalDate departureDate, boolean weatherAck, String weatherAckNote) {
        this.party = party;
        this.bookedBy = bookedBy;
        this.tour = tour;
        this.peopleCount = peopleCount;
        this.reservationNumber = reservationNumber;
        this.departureDate = departureDate;
        this.status = ReservationStatus.pending;
        this.weatherAck = weatherAck;
        this.weatherAckNote = weatherAckNote;
    }

    public void confirm() { this.status = ReservationStatus.confirmed; }
    public void cancel() { this.status = ReservationStatus.cancelled; this.cancelledAt = LocalDateTime.now(); }
}
