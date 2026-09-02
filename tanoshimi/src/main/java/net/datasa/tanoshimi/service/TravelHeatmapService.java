package net.datasa.tanoshimi.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.RegionVisitView;
import net.datasa.tanoshimi.domain.dto.TravelHeatmapView;
import net.datasa.tanoshimi.domain.entity.MyTripEntity;
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
 * <p><b>v18 변경 - 개별 여행(파티 없이 혼자 다녀온 여행) 인정.</b> 예전에는 예약 삭제로
 * "혼자 다녀온 여행"을 인정할 근거가 아예 없었다(파티만 셌다). 스냅(posts)에 이미 있는
 * 지역 태그를 근거로 되살렸다 - party_id 가 NULL 인 글(= 어떤 파티에도 안 걸린 개인 스냅)에
 * 지역 태그가 있으면 그 지역을 다녀온 걸로 본다. 위치 태그를 검색해서 다는 UI(인스타
 * 위치태그 같은 것)는 아직 없고 지금 있는 자유 입력 그대로 쓴다 - 나중에 오채원(④)이
 * 그 UI를 붙여도 posts.region 필드 자체는 그대로라 이 로직은 안 고쳐도 된다.
 *
 * <p>party_id 가 있는 글(= 파티 인증 스냅)은 여기서 세지 않는다 - 그 파티가 완료되면
 * 위 파티 집계에서 이미 잡히므로, 또 세면 같은 여행이 두 번 잡힌다.
 *
 * <p>같은 날 같은 지역에 사진을 여러 장 올려도 하루치(1회·1일)로만 인정한다 - 사진 개수로
 * 여행 횟수를 부풀릴 수 없게. 개별 여행은 파티처럼 지속일수를 알 수 없어 항상 최소 1일로 본다.
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

    /**
     * "내 여행"(my_trips) 목록을 지역별로 묶어 히트맵 데이터를 만든다.
     *
     * <p><b>v19 변경</b> - parties/posts 를 직접 스캔하던 걸 my_trips 목록 하나로 옮겼다.
     * {@code trips} 는 MyPageController 가 MyTripService.listMine 으로 동기화·조회한 뒤,
     * {@link net.datasa.tanoshimi.service.MyTripService#isCountable} 로 걸러낸 "실제로
     * 카운트할" 목록만 넘겨준다 - TitleService.syncTitles 와 같은 근거를 공유해야 지도 색과
     * 칭호가 "몇 번 다녀왔는지"를 다르게 말하지 않는다.
     */
    @Transactional(readOnly = true)
    public TravelHeatmapView summarize(List<MyTripEntity> trips) {
        // region -> [횟수, 일수]. 화면에 나열되는 순서를 안정적으로 두려고 LinkedHashMap 사용.
        Map<String, int[]> accumulator = new LinkedHashMap<>();
        for (MyTripEntity trip : trips) {
            String region = trip.getDestination();
            if (region == null || region.isBlank()) {
                continue;
            }
            int[] cell = accumulator.computeIfAbsent(region.trim(), key -> new int[2]);
            cell[0] += 1;
            cell[1] += (int) Math.max(1, trip.days());
        }

        Map<String, RegionVisitView> regions = new LinkedHashMap<>();
        accumulator.forEach((region, cell) -> {
            int tripCount = cell[0];
            int days = cell[1];
            regions.put(region, new RegionVisitView(tripCount, days, score(tripCount, days)));
        });

        return new TravelHeatmapView(regions, trips.size(), regions.size());
    }

    static int score(int trips, int days) {
        return trips * SCORE_PER_TRIP + days * SCORE_PER_DAY;
    }
}
