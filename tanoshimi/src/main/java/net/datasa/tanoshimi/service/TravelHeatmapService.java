package net.datasa.tanoshimi.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.RegionVisitView;
import net.datasa.tanoshimi.domain.dto.TravelHeatmapView;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 마이페이지 "지도 정복 히트맵" 집계. 담당: 김민규(⑥).
 *
 * <p>v16 이전에는 예약(reservations) 내역의 tour.region 으로 방문 횟수를 셌다. 예약·결제
 * 기능이 삭제되면서 그 근거가 사라져, 완료된 파티(PartyStatus.completed)의 region 을
 * 집계 기준으로 옮겼다. 파티의 완료 전환은 PartyCompletionScheduler(⑤)가 매일 새벽에
 * 여행 종료일(departureDate + durationDays)을 보고 자동으로 수행한다.
 *
 * <p><b>지도 색은 단순 횟수가 아니라 "여행 지수"로 칠한다.</b> 당일치기 한 번과 일주일
 * 여행 한 번이 똑같은 1회로 보이는 게 어색해서, 횟수와 여행일수를 함께 반영했다.
 * 화면에 숫자로 노출되는 "N회 여행"은 지수가 아니라 실제 완료 횟수 그대로다
 * (사용자에게 보이는 숫자와 색의 근거를 분리).
 *
 * <p>가중치와 단계 경계를 바꾸고 싶으면 아래 상수와 mypage-heatmap.js 의 TIERS 만
 * 같이 고치면 된다.
 */
@Service
@RequiredArgsConstructor
public class TravelHeatmapService {

    /** 완료한 여행 1건당 기본 점수. */
    static final int SCORE_PER_TRIP = 3;

    /** 여행 하루당 추가 점수. */
    static final int SCORE_PER_DAY = 1;

    private final PartyMemberRepository partyMemberRepository;

    /** 사용자의 완료된 여행을 지역별로 묶어 히트맵 데이터를 만든다. */
    @Transactional(readOnly = true)
    public TravelHeatmapView summarize(UserEntity user) {
        List<PartyEntity> completed =
                partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed);

        // region -> [횟수, 일수]. 화면에 나열되는 순서를 안정적으로 두려고 LinkedHashMap 사용.
        Map<String, int[]> accumulator = new LinkedHashMap<>();
        for (PartyEntity party : completed) {
            String region = party.getRegion();
            if (region == null || region.isBlank()) {
                continue;
            }
            int[] cell = accumulator.computeIfAbsent(region.trim(), key -> new int[2]);
            cell[0] += 1;
            // durationDays 는 NOT NULL DEFAULT 1 이지만, 과거 데이터에 0이 들어있을 수 있어 최소 1일로 본다.
            cell[1] += Math.max(1, party.getDurationDays());
        }

        Map<String, RegionVisitView> regions = new LinkedHashMap<>();
        accumulator.forEach((region, cell) -> {
            int trips = cell[0];
            int days = cell[1];
            regions.put(region, new RegionVisitView(trips, days, score(trips, days)));
        });

        return new TravelHeatmapView(regions, completed.size(), regions.size());
    }

    static int score(int trips, int days) {
        return trips * SCORE_PER_TRIP + days * SCORE_PER_DAY;
    }
}
