package net.datasa.tanoshimi.domain.dto;

/** 계획표 화면(JS)에 그대로 내려주는 블록 뷰. */
public record ScheduleItemView(
        Long id, int dayIndex, int startMinute, int durationMinute,
        String source, String title, String memo, int priceKrw, int priceJpy,
        Long addedByUserId, String addedByName
) {
}
