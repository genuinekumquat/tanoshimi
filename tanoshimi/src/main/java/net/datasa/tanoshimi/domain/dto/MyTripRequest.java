package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * "내 여행" 추가/수정 요청.
 *
 * @param destination 위치태그 선택이 아니라 자유 입력(사용자가 타이핑) - MyTripEntity 클래스 주석 참고.
 */
public record MyTripRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 100) String destination,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 500) String memo
) {
}
