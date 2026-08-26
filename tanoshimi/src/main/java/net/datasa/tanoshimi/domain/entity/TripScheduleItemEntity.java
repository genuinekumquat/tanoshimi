package net.datasa.tanoshimi.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "trip_schedule_items")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripScheduleItemEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "schedule_id", nullable = false)
    private TripScheduleEntity schedule;

    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "activity_id")
    private ActivityEntity activity;

    @Column(name = "day_index", nullable = false)
    private byte dayIndex;

    @Column(name = "start_minute", nullable = false)
    private short startMinute;

    @Column(name = "duration_minute", nullable = false)
    private short durationMinute;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleItemSource source;

    /**
     * [v16 신규] true=고정(LOCK, 항공·숙박 등 - AI 동선최적화 대상에서 제외되는 앵커),
     * false=이동가능(액티비티 기본값, AI 동선최적화가 재배치할 수 있음).
     * source=package_default 이면 항상 true 로 시작한다(생성자에서 자동 설정).
     */
    @Column(name = "is_fixed", nullable = false, columnDefinition = "boolean default false")
    private boolean isFixed;

    @Column(length = 200)
    private String title;

    @Column(length = 500)
    private String memo;

    @Column(name = "price_krw")
    private Integer priceKrw;

    @Column(name = "price_jpy")
    private Integer priceJpy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "added_by")
    private UserEntity addedBy;

    @Builder
    public TripScheduleItemEntity(TripScheduleEntity schedule, ActivityEntity activity, byte dayIndex,
                                  short startMinute, short durationMinute, ScheduleItemSource source,
                                  String title, String memo, Integer priceKrw, Integer priceJpy, UserEntity addedBy) {
        this.schedule = schedule;
        this.activity = activity;
        this.dayIndex = dayIndex;
        this.startMinute = startMinute;
        this.durationMinute = durationMinute;
        this.source = source == null ? ScheduleItemSource.custom : source;
        if (this.source == ScheduleItemSource.activity && activity == null) {
            throw new IllegalArgumentException("액티비티 항목은 액티비티 연결이 필수입니다.");
        }
        // package_default(항공/체크인 등)는 항상 고정으로 시작한다 - AI 동선최적화가 건드리면 안 되는 앵커.
        this.isFixed = this.source == ScheduleItemSource.package_default;
        if (this.source == ScheduleItemSource.custom && title == null) {
            this.title = "직접 입력"; // fallback
        } else {
            this.title = title;
        }
        this.memo = memo;
        this.priceKrw = priceKrw;
        this.priceJpy = priceJpy;
        this.addedBy = addedBy;
    }

    /** 드래그 혹은 직접 시간 입력 시 칸 크기 및 위치 변경 (드래그 앤 드롭 등). */
    public void reschedule(short newStart, short newDuration, Byte newDayIndex) {
        this.startMinute = newStart;
        this.durationMinute = newDuration;
        if (newDayIndex != null) {
            this.dayIndex = newDayIndex;
        }
    }

    public void rename(String title, String memo) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }
        if (memo != null) {
            this.memo = memo.trim();
        }
    }

    /** [v16 신규] AI 동선최적화가 재배치 대상에서 제외할지 여부를 결정하는 값. */
    public boolean isMovable() { return !isFixed; }

    /** [v16 신규] 스냅샷 롤백 복원 전용 - 저장된 시점의 isFixed 값을 그대로 되살린다. */
    public void restoreFixedFlag(boolean isFixed) { this.isFixed = isFixed; }
}
