package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 계획표 블록 추가/이동/리사이즈 요청.
 * activityId 가 있으면 사이트 제공 유료 액티비티(source=activity),
 * 없으면 사용자가 직접 채우는 빈 칸(source=custom, 무료).
 * startMinute/durationMinute 은 1분 단위 — 사용자가 시간을 입력하면 그 값 그대로 칸 크기가 바뀐다.
 */
public record ScheduleItemRequest(
        @Min(1) int dayIndex,
        @Min(0) int startMinute,
        @Min(1) int durationMinute,
        Long activityId,          // null 이면 custom(빈 칸)
        String title,             // custom 일 때 사용자가 직접 입력
        String memo
) {
}
