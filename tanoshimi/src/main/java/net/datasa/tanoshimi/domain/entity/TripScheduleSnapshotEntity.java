package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * [v16 신규] 계획표 저장 시점 스냅샷 - 자동저장(20분 주기) 또는 수동저장 때마다 1건씩 적재된다.
 * 파티장이 과거 시점을 선택하면 이 스냅샷의 snapshotData(JSON)로 trip_schedule_items 를 복원한다.
 * 롤백 시에는 복원 직전에 "현재 상태"도 먼저 스냅샷으로 남긴 뒤 복원한다(되돌리기의 되돌리기 방지).
 */
@Entity
@Getter
@Table(name = "trip_schedule_snapshots")
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripScheduleSnapshotEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_id", nullable = false)
    private TripScheduleEntity schedule;

    /** 저장 시점의 전체 trip_schedule_items 를 그대로 담은 JSON 문자열. */
    @Lob
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "JSON")
    private String snapshotData;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 10)
    private SnapshotTrigger triggerType;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "created_by", nullable = false)
    private UserEntity createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public TripScheduleSnapshotEntity(TripScheduleEntity schedule, String snapshotData,
                                      SnapshotTrigger triggerType, UserEntity createdBy) {
        this.schedule = schedule;
        this.snapshotData = snapshotData;
        this.triggerType = triggerType;
        this.createdBy = createdBy;
    }
}
