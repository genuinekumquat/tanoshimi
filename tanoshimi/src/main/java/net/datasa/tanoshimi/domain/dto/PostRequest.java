package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 게시판/마이페이지 공용 글쓰기 요청 - 같은 데이터를 두 화면에서 쓰는 것과 마찬가지로 작성 폼도 공유. */
public record PostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        String region,
        String thumbnailUrl,
        Long partyId
) {
}
