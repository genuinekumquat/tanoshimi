package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.dto.ReserveTourRequest;
import net.datasa.tanoshimi.domain.dto.WeatherAdviceDTO;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.TourEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.TourRepository;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

/**
 * 항공+숙박 패키지 화면 + 예약(결제 1단계).
 *
 * <p>예약 흐름: ① /api/packages/{id}/weather 로 미리보기(챗봇 경고) -> ② 사용자가 진행/취소 선택
 * -> ③ 진행하면 /api/packages/{id}/reserve 호출(날씨 안 좋았으면 weatherAck=true 로 재확인).
 */
@Controller
@RequiredArgsConstructor
public class PackageController {

    private final TourRepository tourRepository;
    private final UserRepository userRepository;
    private final ReservationService reservationService;

    @GetMapping("/packages")
    public String list(@RequestParam(required = false) String region,
                       @RequestParam(required = false) String sort,
                       @RequestParam(required = false) Long partyId, Model model) {
        boolean hasRegion = region != null && !region.isBlank();
        List<TourEntity> tours;
        if ("price_low".equals(sort)) {
            tours = hasRegion ? tourRepository.findByRegionAndStatusOrderByPriceKrwAsc(region, ActiveStatus.active)
                              : tourRepository.findByStatusOrderByPriceKrwAsc(ActiveStatus.active);
        } else if ("price_high".equals(sort)) {
            tours = hasRegion ? tourRepository.findByRegionAndStatusOrderByPriceKrwDesc(region, ActiveStatus.active)
                              : tourRepository.findByStatusOrderByPriceKrwDesc(ActiveStatus.active);
        } else {
            tours = hasRegion ? tourRepository.findByRegionAndStatus(region, ActiveStatus.active)
                              : tourRepository.findByStatusOrderByIdDesc(ActiveStatus.active);
        }
        model.addAttribute("tours", tours);
        model.addAttribute("partyId", partyId);
        model.addAttribute("selectedRegion", region);
        model.addAttribute("selectedSort", sort);
        return "packages/list";
    }

    @GetMapping("/packages/{id}")
    public String detail(@PathVariable Long id, Model model) {
        TourEntity tour = tourRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
        model.addAttribute("tour", tour);
        return "packages/detail";
    }

    /** 챗봇 날씨 조언 미리보기 - 예약 버튼을 누르기 전에 프론트가 이걸로 경고 다이얼로그를 그린다. */
    @GetMapping("/api/packages/{id}/weather")
    @ResponseBody
    public ApiResponse<WeatherAdviceDTO> weatherPreview(@PathVariable Long id,
                                                        @RequestParam("date") String date) {
        WeatherAdviceDTO advice = reservationService.previewWeather(id, LocalDate.parse(date));
        return ApiResponse.ok(advice);
    }

    @PostMapping("/api/packages/{id}/reserve")
    @ResponseBody
    public ApiResponse<Long> reserve(@PathVariable Long id, @Valid @RequestBody ReserveTourRequest rawRequest,
                                     @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity booker = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        // 경로변수 id 를 신뢰 - 요청 바디의 tourId 와 다르면 경로 값을 우선한다(둘 다 채워야 하는 폼 특성상 방어적으로 재구성)
        ReserveTourRequest request = new ReserveTourRequest(id, rawRequest.partyId(), rawRequest.departureDate(),
                rawRequest.peopleCount(), rawRequest.weatherAck());
        Long reservationId = reservationService.reserve(booker, request);
        return ApiResponse.ok("예약이 완료되었습니다.", reservationId);
    }

    /**
     * 패키지 결제(포인트 차감) - 예약(reserve)과 별개의 단계다.
     * 예약만 하고 이 엔드포인트를 안 부르면 reservation_payments 가 'ready' 상태로 영원히 남는다.
     * (마이페이지 "내 예약" 화면의 "결제하기" 버튼에서 호출한다)
     */
    @PostMapping("/api/reservations/{reservationId}/pay")
    @ResponseBody
    public ApiResponse<Void> pay(@PathVariable Long reservationId, @AuthenticationPrincipal CustomUserDetails principal) {
        UserEntity payer = userRepository.findById(principal.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        reservationService.pay(reservationId, payer);
        return ApiResponse.okMessage("결제가 완료되었습니다.");
    }
}
