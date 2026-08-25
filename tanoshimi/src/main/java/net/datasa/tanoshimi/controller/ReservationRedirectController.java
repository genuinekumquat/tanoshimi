package net.datasa.tanoshimi.controller;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.ReservationPaymentEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ReservationPaymentRepository;
import net.datasa.tanoshimi.repository.ReservationRepository;
import net.datasa.tanoshimi.repository.TripScheduleRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 결제 전용 페이지 화면 컨트롤러.
 * 예약 직후 플래너로 직행하는 대신, 결제 내역과 차감 포인트를 명시적으로 보여주는 Checkout 화면을 띄워줍니다.
 */
@Controller
@RequiredArgsConstructor
public class ReservationRedirectController {

    private final ReservationRepository reservationRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final ReservationPaymentRepository reservationPaymentRepository;
    private final UserRepository userRepository;

    @GetMapping("/reservations/{id}")
    public String checkout(@PathVariable Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        ReservationEntity reservation = reservationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
                
        var schedule = tripScheduleRepository.findByReservation(reservation)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

        ReservationPaymentEntity payment = reservationPaymentRepository.findByReservationAndUser(reservation, me)
                .orElse(null);

        // 모델에 예약 및 결제 정보 전달
        model.addAttribute("reservation", reservation);
        model.addAttribute("tour", reservation.getTour());
        model.addAttribute("scheduleId", schedule.getId());
        model.addAttribute("payment", payment);
        model.addAttribute("user", me);
        
        // 결제가 이미 완료되었으면 바로 플래너로 넘깁니다.
        if (payment != null && payment.getStatus().name().equals("completed")) {
             return "redirect:/planner/" + schedule.getId();
        }

        return "reservations/checkout";
    }
}
