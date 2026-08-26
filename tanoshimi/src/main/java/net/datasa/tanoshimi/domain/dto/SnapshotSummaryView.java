package net.datasa.tanoshimi.domain.dto;

/** [v16 신규] 계획표 롤백 화면에 보여줄 저장 시점 요약. */
public record SnapshotSummaryView(Long id, String triggerType, String createdByName, String createdAt) {
}
