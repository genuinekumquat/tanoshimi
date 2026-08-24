package net.datasa.tanoshimi.domain.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 패키지 예약 요청.
 * weatherAck: 프론트에서 WeatherAdviceDTO.recommend()==false 인데도 사용자가
 * "그래도 진행할게요" 를 눌렀을 때만 true 로 넘어온다. 서버는 이 값을 그대로 믿지 않고
 * 예약 시점에 WeatherAdvisorService 로 한번 더 조회해서 스냅샷 문구를 저장한다.
 */
public record ReserveTourRequest(
        @NotNull Long tourId,
        Long partyId,               // 개인 예약이면 null
        @NotNull LocalDate departureDate,
        @Min(1) int peopleCount,
        boolean weatherAck
) {
}
