package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 여행 계획표.
 *
 * <p>파티를 만들면 예약 전이라도 즉시 계획표가 생긴다(party 로 연결, reservation 은 null).
 * 이 상태에서도 자유롭게 액티비티/빈 칸을 넣어보며 "임시로" 계획을 짜볼 수 있다.
 * 실제 패키지를 예약/결제하면 그 순간 reservation 이 채워지고, 항공/체크인 등
 * package_default 블록이 추가되며 그 블록들은 이후 영구히 고정(수정 불가)된다.
 * 파티 없이 혼자 여행하는 사람은 reservation 만으로 연결된다(party 는 null).
 *
 * <p>draft: 자유 편집(액티비티 결제의무 없음) -> submitted: 액티비티 결제 대기 -> confirmed: 결제 완료.
 */
@Entity
@Getter
@Table(name = "trip_schedules")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripScheduleEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "party_id")
    private PartyEntity party;

    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reservation_id", unique = true)
    private ReservationEntity reservation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScheduleStatus status;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 파티 생성 시점 - 아직 패키지 예약 전이라도 바로 계획표를 만든다. */
    public TripScheduleEntity(PartyEntity party) {
        this.party = party;
        this.status = ScheduleStatus.draft;
    }

    /** 혼자(파티 없이) 예약하는 개인 이용자용. */
    public TripScheduleEntity(ReservationEntity reservation) {
        this.reservation = reservation;
        this.status = ScheduleStatus.draft;
    }

    /** 패키지를 실제로 예약한 시점에 호출 - 기존 초안 계획표에 예약을 연결한다. */
    public void linkReservation(ReservationEntity reservation) {
        this.reservation = reservation;
    }

    public boolean isDraft() { return status == ScheduleStatus.draft; }
    public boolean hasReservation() { return reservation != null; }

    public void submit() {
        this.status = ScheduleStatus.submitted;
        this.submittedAt = LocalDateTime.now();
    }

    public void confirm() {
        this.status = ScheduleStatus.confirmed;
        this.confirmedAt = LocalDateTime.now();
    }
}
