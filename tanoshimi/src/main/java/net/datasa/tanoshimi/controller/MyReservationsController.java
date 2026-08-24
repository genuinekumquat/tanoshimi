package net.datasa.tanoshimi.controller;

import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.MyReservationView;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.ReservationPaymentRepository;
import net.datasa.tanoshimi.repository.ReservationRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 내 예약 내역 - 지금까지 어디 화면에도 없던 기능이라 새로 만들었다.
 * 예약(패키지) + 내 결제 상태를 한눈에 보여준다.
 */
@Controller
@RequiredArgsConstructor
public class MyReservationsController {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPaymentRepository reservationPaymentRepository;

    @GetMapping("/my-reservations")
    public String list(@AuthenticationPrincipal CustomUserDetails principal, Model model) {
        UserEntity me = userRepository.findById(principal.getId()).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<ReservationEntity> reservations = reservationRepository.findByBookedBy(me);
        List<MyReservationView> views = reservations.stream()
                .map(r -> {
                    var payment = reservationPaymentRepository.findByReservationAndUser(r, me).orElse(null);
                    return new MyReservationView(
                            r.getId(), r.getTour().getTitle(), r.getTour().getThumbnailUrl(), r.getTour().getRegion(),
                            r.getDepartureDate().format(DATE_FMT), r.getStatus().name(),
                            payment != null ? payment.getStatus().name() : "-",
                            payment != null ? payment.getCurrency().name() : "",
                            payment != null ? payment.getAmount() : null);
                })
                .sorted((a, b) -> b.departureDate().compareTo(a.departureDate()))
                .toList();

        model.addAttribute("reservations", views);
        return "mypage/reservations";
    }
}
