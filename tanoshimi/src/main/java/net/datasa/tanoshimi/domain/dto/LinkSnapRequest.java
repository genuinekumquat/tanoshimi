package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotNull;

/** [v20 신규] "내 여행" 관리 화면에서 기존 스냅을 여행에 연결할 때(MyTripController) 쓰는 요청. */
public record LinkSnapRequest(@NotNull Long postId) {
}
