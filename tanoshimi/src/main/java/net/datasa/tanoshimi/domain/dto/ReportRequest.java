package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 신고 접수 요청 - 게시글/파티/사용자 공용. */
public record ReportRequest(
        @NotBlank String targetType,     // post / party / user
        @NotNull Long targetId,
        @NotBlank String targetLabel,    // 신고 시점 제목/이름 스냅샷
        @NotBlank String reason
) {
}
