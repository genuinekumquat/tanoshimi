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

    /**
     * 편집권을 가진 파티원. 기본적으로 파티장이 항상 가지고 있으며(생성자에서 자동 설정),
     * 파티장이 특정 파티원에게 실시간으로 부여했다가 다시 회수할 수 있다(한 번에 한 명뿐).
     * "회수"는 null 이 아니라 파티장에게 되돌리는 것을 의미한다 - TripPlannerLockService 참고.
     */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "locked_by_user_id")
    private UserEntity lockedBy;

    /** 마지막 저장(자동 10분 주기 또는 수동) 시각 - 화면 상단 표시용. */
    @Column(name = "last_saved_at")
    private LocalDateTime lastSavedAt;

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

    /** 파티 생성 시점 - 아직 패키지 예약 전이라도 바로 계획표를 만든다. 편집권은 기본적으로 파티장이 가진다. */
    public TripScheduleEntity(PartyEntity party) {
        this.party = party;
        this.status = ScheduleStatus.draft;
        this.lockedBy = party.getOwner();
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

    /**
     * 파티장이 특정 파티원에게 편집권을 부여한다. null 을 넘기지 않는다 - "회수"는
     * TripPlannerLockService.revokeLock() 에서 파티장에게 되돌리는 방식으로 처리한다
     * (기본적으로 파티장만 갖는다는 원칙을 유지하기 위해, 편집권 없는 상태는 만들지 않는다).
     */
    public void setLockedBy(UserEntity user) { this.lockedBy = user; }

    /**
     * 이 사용자가 지금 편집할 수 있는지. lockedBy 가 비어있는 레거시 데이터를 만나도
     * 안전하게 "파티장 기본 권한"으로 판단하도록 party.owner 도 함께 확인한다.
     */
    public boolean isLockedBy(Long userId) {
        if (lockedBy != null) {
            return lockedBy.getId().equals(userId);
        }
        return party != null && party.getOwner().getId().equals(userId);
    }

    /** 자동(10분 주기)/수동 저장 시각 갱신. 저장 때마다 호출되며 스냅샷도 함께 남는다(서비스 계층 책임). */
    public void touchSaved() { this.lastSavedAt = LocalDateTime.now(); }
}
