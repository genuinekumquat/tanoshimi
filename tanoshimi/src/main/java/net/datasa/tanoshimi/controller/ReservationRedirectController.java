package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ReservationRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 예약 완료 직후 리다이렉트 전용 - reservationId 를 받아 그 예약에 딸린 계획표(scheduleId)로 넘겨준다.
 * 예약 자체를 보여주는 화면은 아직 없어서(다음 단계), 지금은 바로 플래너로 보낸다.
 */
@Controller
@RequiredArgsConstructor
public class ReservationRedirectController {

    private final ReservationRepository reservationRepository;
    private final TripScheduleRepository tripScheduleRepository;

    @GetMapping("/reservations/{id}")
    public String toPlanner(@PathVariable Long id) {
        ReservationEntity reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        var schedule = tripScheduleRepository.findByReservation(reservation)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
        return "redirect:/planner/" + schedule.getId();
    }
}
