package net.datasa.tanoshimi.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ReserveTourRequest;
import net.datasa.tanoshimi.domain.dto.WeatherAdviceDTO;
import net.datasa.tanoshimi.domain.entity.*;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 1단계: 항공+숙박 패키지 예약.
 * 파티가 있으면 party_members 전원에게 인원수별로 나누어 n빵을 reservation_payments 로 만들고,
 * 혼합 파티라면 각자 자기 나라 통화(KRW/JPY)로 자기 몫만 결제하게 된다(환전 없음).
 * 결제가 덜어지면 돈을 많이 낸 사람에게 몰아준다던지 하는 문제는 사다리게임으로 다음 단계에서 해결.
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final TourRepository tourRepository;
    private final PartyRepository partyRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationPaymentRepository reservationPaymentRepository;
    private final TripScheduleRepository tripScheduleRepository;
    private final WeatherAdvisorService weatherAdvisorService;
    private final TripPlannerService tripPlannerService;
    private final PartyService partyService;
    private final TitleService titleService;
    private final UserRepository userRepository;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** 예약 전 미리보기 - 화면에서 예약하기 누르기 전에 이 결과로 날씨 경고 다이얼로그를 띄운다 */
    @Transactional(readOnly = true)
    public WeatherAdviceDTO previewWeather(Long tourId, LocalDate departureDate) {
        TourEntity tour = tourRepository.findById(tourId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));
        return weatherAdvisorService.adviseForTour(tour, departureDate);
    }

    @Transactional
    public Long reserve(UserEntity booker, ReserveTourRequest request) {
        TourEntity tour = tourRepository.findById(request.tourId())
                .orElseThrow(() -> new BusinessException(ErrorCode.TOUR_NOT_FOUND));

        PartyEntity party = null;
        List<UserEntity> payers;
        if (request.partyId() != null) {
            party = partyRepository.findById(request.partyId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARTY_NOT_FOUND));
            payers = partyMemberRepository.findByParty(party).stream().map(PartyMemberEntity::getUser).toList();
        } else {
            payers = List.of(booker);
        }

        // 날씨 재확인(클라이언트 값을 그대로 믿지 않는다) 후 스냅샷 문구만 저장
        WeatherAdviceDTO advice = weatherAdvisorService.adviseForTour(tour, request.departureDate());
        if (!advice.recommend() && !request.weatherAck()) {
            throw new BusinessException(ErrorCode.WEATHER_ACK_REQUIRED);
        }

        ReservationEntity reservation = ReservationEntity.builder()
                .party(party)
                .bookedBy(booker)
                .tour(tour)
                .peopleCount((byte) request.peopleCount())
                .reservationNumber(generateReservationNumber())
                .departureDate(request.departureDate())
                .weatherAck(!advice.recommend())
                .weatherAckNote(advice.recommend() ? null : advice.weather().condition() + " 감수하고 예약")
                .build();
        reservationRepository.save(reservation);

        splitPayment(reservation, tour, payers);

        // 계획표 연결: 파티가 있으면 파티 생성 시점에 이미 만들어둔 초안 계획표에 예약을 연결하고
        // (미리 짜둔 임시 계획이 있어도 그대로 유지된다), 개인 예약이면 새로 하나 만든다.
        // 이 시점에 패키지 이동/체크인 블록이 추가되고, 그 블록들은 이후 영구 고정된다
        // ("항공편 시간은 플래너에서 고정" 요구사항).
        TripScheduleEntity schedule = (party != null)
                ? partyService.ensureSchedule(party)
                : tripScheduleRepository.save(new TripScheduleEntity(reservation));
        schedule.linkReservation(reservation);
        tripPlannerService.initializeDefaults(schedule, booker);

        return reservation.getId();
    }

    /** 인원별 KRW/JPY 분할. 각자 자기 나라 통화로 자기 몫만 결제한다(환전 없음). */
    private void splitPayment(ReservationEntity reservation, TourEntity tour, List<UserEntity> payers) {
        int n = payers.size();
        for (int i = 0; i < n; i++) {
            UserEntity payer = payers.get(i);
            boolean isJpy = payer.getNationality() == Nationality.JP;
            int unitPrice = isJpy ? tour.getPriceJpy() : tour.getPriceKrw();
            int amount = unitPrice; // 1인 기준 가격이므로 인원수 분할이 아니라 개인당 정가
            reservationPaymentRepository.save(new ReservationPaymentEntity(
                    reservation, payer, isJpy ? Currency.JPY : Currency.KRW, amount));
        }
    }

    /** 결제 실행(포인트 차감). 데모용 - 실제 PG 연동 없음. */
    @Transactional
    public void pay(Long reservationId, UserEntity payer) {
        ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESERVATION_NOT_FOUND));
        ReservationPaymentEntity payment = reservationPaymentRepository.findByReservationAndUser(reservation, payer)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

        if (!payer.deductPoints(payment.getCurrency(), payment.getAmount())) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_POINTS);
        }
        payment.markPaid();
        // payer 는 컨트롤러(비-트랜잭션 컨텍스트)에서 미리 조회해온 detached 엔티티다.
        // deductPoints() 로 포인트를 깎아도 save() 가 없으면 실제로는 반영이 안 된다
        // ("결제했는데 포인트가 그대로인" 현상 - PostService.toggleLike 와 동일한 버그 패턴).
        userRepository.save(payer);

        long paidCount = reservationPaymentRepository.countByReservationAndStatus(reservation, PaymentStatus.paid);
        long totalCount = reservationPaymentRepository.findByReservation(reservation).size();
        if (paidCount == totalCount) {
            reservation.confirm();
            // 예약이 확정된 시점에 여행 횟수 기반 칭호(숙련된 여행자/베테랑 탐험가)를 다시 확인한다
            reservationPaymentRepository.findByReservation(reservation).forEach(p -> titleService.checkTripCountTitles(p.getUser()));
        }
    }

    private String generateReservationNumber() {
        return "TNS" + System.currentTimeMillis() % 100000000L + RANDOM.nextInt(90) + 10;
    }
}
