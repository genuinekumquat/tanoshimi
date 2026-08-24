package net.datasa.tanoshimi.scheduler;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyMemberEntity;
import net.datasa.tanoshimi.domain.entity.ReservationEntity;
import net.datasa.tanoshimi.domain.entity.ReservationStatus;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.ReservationRepository;
import net.datasa.tanoshimi.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * "여행 계획 하루 전에 홈페이지에서 알림이 오게 해 줘" 요구사항.
 *
 * <p>매일 새벽 8시에 내일 출발하는 확정 예약을 찾아 파티원 전원(또는 개인 이용자)에게
 * 인앱 알림을 하나씩 만든다. 푸시/이메일 발송은 하지 않는다(인프라 범위 밖 - 다음 단계 논의 필요).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TripReminderScheduler {

    private final ReservationRepository reservationRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *")
    public void notifyTomorrowsTrips() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        List<ReservationEntity> allConfirmed = reservationRepository.findByDepartureDateAndStatus(tomorrow, ReservationStatus.confirmed);

        int notified = 0;
        for (ReservationEntity reservation : allConfirmed) {
            PartyEntity party = reservation.getParty();
            String title = "내일 출발하는 여행이 있어요";
            String message = reservation.getTour().getTitle() + " — 짐 챙기는 거 잊지 마세요!";
            String link = "/reservations/" + reservation.getId();

            if (party == null) {
                notificationService.notify(reservation.getBookedBy(), "trip_reminder", title, message, link);
                notified++;
            } else {
                for (PartyMemberEntity member : partyMemberRepository.findByParty(party)) {
                    notificationService.notify(member.getUser(), "trip_reminder", title, message, link);
                    notified++;
                }
            }
        }
        log.info("여행 하루 전 알림 발송: {}건 (기준일 {})", notified, tomorrow);
    }
}
