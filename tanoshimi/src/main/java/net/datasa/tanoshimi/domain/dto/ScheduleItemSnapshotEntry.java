package net.datasa.tanoshimi.domain.dto;

/**
 * [v16 신규] trip_schedule_snapshots.snapshot_data(JSON) 한 줄에 해당하는 항목 - 스냅샷을
 * 완전히 복원하는 데 필요한 필드를 전부 담는다(ScheduleItemView 는 화면 표시용이라 activityId,
 * isFixed 가 빠져있어 복원용으로는 부족하므로 별도 DTO로 분리).
 */
public record ScheduleItemSnapshotEntry(
        Long activityId, int dayIndex, int startMinute, int durationMinute,
        String source, boolean isFixed, String title, String memo,
        int priceKrw, int priceJpy, Long addedByUserId
) {
}
