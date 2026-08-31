package net.datasa.tanoshimi.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.TitleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserTitleEntity;
import net.datasa.tanoshimi.repository.TitleRepository;
import net.datasa.tanoshimi.repository.UserTitleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칭호 자동 부여. 담당: 김민규(⑥).
 *
 * <p><b>v16 변경</b> - 이전에는 "예약이 confirmed 된 횟수"로 등급 칭호를 줬다. 예약·결제
 * 기능이 삭제되면서 그 기준 자체가 사라졌고, 부여 시점이던 ReservationService.pay() 도
 * 없어질 예정이라 그대로 두면 등급 칭호가 영영 부여되지 않는다. 그래서 기준을
 * <b>완료된 파티 수</b>와 <b>다녀온 지역 수</b>로 바꿨다(TravelHeatmapService 와 같은 근거).
 *
 * <p>부여 시점:
 * <ul>
 *   <li>NEWBIE — 회원가입 직후 (UserService.signup / signupSocial 에서 호출)</li>
 *   <li>EXPLORER / VETERAN / COLLECTOR — 마이페이지를 열 때 실적을 다시 세서 확인</li>
 * </ul>
 *
 * <p>※ 원래는 파티가 완료되는 시점(PartyCompletionScheduler)에 한 번만 확인하는 게
 * 맞다. 그 스케줄러는 ⑤(허수연) 소유라 임의로 고치지 않고, 우선 마이페이지 조회
 * 시점에 맞추도록 두었다. 호출부 추가는 협의 후 옮길 것. syncTravelTitles 는 몇 번을
 * 호출해도 결과가 같도록(멱등) 만들어 두었으니 옮겨도 부작용이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleService {

    /** 숙련된 여행자 - 완료한 여행 횟수 기준. */
    static final int EXPLORER_TRIPS = 3;

    /** 베테랑 탐험가 - 완료한 여행 횟수 기준. */
    static final int VETERAN_TRIPS = 10;

    /** 지도 수집가 - 다녀온 지역 수 기준. */
    static final int COLLECTOR_REGIONS = 5;

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;

    @Transactional
    public void awardNewbie(UserEntity user) {
        award(user, "NEWBIE");
    }

    /**
     * 완료한 여행 실적으로 등급·수집 칭호를 확인한다.
     *
     * <p>이미 가진 칭호는 건너뛰므로 여러 번 호출해도 안전하다. 한 번 딴 칭호는
     * 실적이 줄어도 회수하지 않는다(수집 요소라 뺏기면 기분이 나쁘다).
     */
    @Transactional
    public void syncTravelTitles(UserEntity user, int completedTrips, int visitedRegions) {
        if (completedTrips >= VETERAN_TRIPS) {
            award(user, "VETERAN");
        } else if (completedTrips >= EXPLORER_TRIPS) {
            award(user, "EXPLORER");
        }
        if (visitedRegions >= COLLECTOR_REGIONS) {
            award(user, "COLLECTOR");
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

    /** 마이페이지 뱃지에 보여줄 "가장 최근에 딴 칭호" - 없으면 null. */
    @Transactional(readOnly = true)
    public TitleEntity latestTitle(UserEntity user) {
        return userTitleRepository.findByUserOrderByEarnedAtDesc(user).stream()
                .findFirst()
                .map(UserTitleEntity::getTitle)
                .orElse(null);
    }

    /**
     * 보유한 칭호 전체(최근 획득 순). 마이페이지 칭호 목록용.
     *
     * <p>장착(equipped) 기능은 user_titles 에 컬럼이 없어 아직 못 만든다 - 스키마 변경은
     * ⑤(허수연) 조율 사항이라 협의 후 추가 예정. 지금은 목록 표시까지만.
     */
    @Transactional(readOnly = true)
    public List<TitleEntity> ownedTitles(UserEntity user) {
        return userTitleRepository.findByUserOrderByEarnedAtDesc(user).stream()
                .map(UserTitleEntity::getTitle)
                .toList();
    }
}
