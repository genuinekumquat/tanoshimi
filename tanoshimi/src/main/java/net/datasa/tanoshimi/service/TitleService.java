package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.ReservationStatus;
import net.datasa.tanoshimi.domain.entity.TitleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserTitleEntity;
import net.datasa.tanoshimi.repository.ReservationRepository;
import net.datasa.tanoshimi.repository.TitleRepository;
import net.datasa.tanoshimi.repository.UserTitleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칭호 자동 부여.
 *
 * <p>DB에 titles/user_titles 테이블은 있었지만 실제로 아무도 부여받지 못하는 상태였다
 * (마이페이지는 방문횟수를 클라이언트에서 계산해 하드코딩된 문구를 보여주고 있었을 뿐,
 * 실제 user_titles 레코드와는 무관했음). 이제 진짜 조건을 확인해서 부여한다.
 *
 * <p>부여 시점:
 * <ul>
 *   <li>NEWBIE — 회원가입 직후 (UserService.signup/signupSocial 에서 호출)</li>
 *   <li>EXPLORER/VETERAN — 예약이 confirmed(결제 완료) 될 때마다 누적 여행 횟수를 다시 세서 확인
 *       (ReservationService.pay 에서 호출)</li>
 *   <li>FLEX — 정산 잔돈 미니게임은 아직 구현 전이라 자동 부여 대상에서 제외(다음 단계)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleService {

    private static final int EXPLORER_THRESHOLD = 15;
    private static final int VETERAN_THRESHOLD = 40;

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;
    private final ReservationRepository reservationRepository;

    @Transactional
    public void awardNewbie(UserEntity user) {
        award(user, "NEWBIE");
    }

    /** 예약이 confirmed 될 때 호출 - 누적 완료 여행 횟수를 다시 세서 등급 칭호를 확인한다. */
    @Transactional
    public void checkTripCountTitles(UserEntity user) {
        long completedTrips = reservationRepository.findByBookedBy(user).stream()
                .filter(r -> r.getStatus() == ReservationStatus.confirmed)
                .count();

        if (completedTrips >= VETERAN_THRESHOLD) {
            award(user, "VETERAN");
        } else if (completedTrips >= EXPLORER_THRESHOLD) {
            award(user, "EXPLORER");
        }
    }

    private void award(UserEntity user, String titleCode) {
        titleRepository.findByCode(titleCode).ifPresentOrElse(title -> {
            if (!userTitleRepository.existsByUserAndTitle(user, title)) {
                userTitleRepository.save(new UserTitleEntity(user, title));
                log.info("칭호 부여: userId={}, title={}", user.getId(), titleCode);
            }
        }, () -> log.warn("칭호 코드 '{}' 를 찾을 수 없습니다 (titles 테이블 시드 확인 필요)", titleCode));
    }

    /** 마이페이지에 보여줄 "가장 최근에 딴 칭호" - 없으면 null. */
    @Transactional(readOnly = true)
    public TitleEntity latestTitle(UserEntity user) {
        return userTitleRepository.findByUserOrderByEarnedAtDesc(user).stream()
                .findFirst()
                .map(UserTitleEntity::getTitle)
                .orElse(null);
    }
}
