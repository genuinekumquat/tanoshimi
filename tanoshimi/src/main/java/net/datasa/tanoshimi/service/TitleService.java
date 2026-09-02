package net.datasa.tanoshimi.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.MyTripEntity;
import net.datasa.tanoshimi.domain.entity.TitleEntity;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.UserTitleEntity;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.repository.PartyRepository;
import net.datasa.tanoshimi.repository.TitleRepository;
import net.datasa.tanoshimi.repository.UserTitleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칭호 자동 부여. 담당: 김민규(⑥).
 *
 * <p><b>v17 변경</b> - 칭호를 목업 기준 38종 8카테고리로 개편했다(마이그레이션
 * {@code db/migration_v17_titles_catalog.sql}). 이전 4종(NEWBIE/EXPLORER/VETERAN/COLLECTOR)은
 * 사라졌고, 코드 접두사로 카테고리를 구분한다.
 * <pre>
 *   T=여행 횟수 / R=국내 지역 다양성 / M=광역시 특색 / J=일본 권역
 *   MAN=매너온도 / P=파티 활동 / D=여행 거리 / A=액티비티
 * </pre>
 *
 * <p><b>v18 변경 ①</b> - 여행 거리 칭호에 상위 2종(D20000/D40000, "지구 반바퀴/한바퀴 클럽")을
 * 추가해 40종이 됐다(마이그레이션 {@code db/migration_v18_titles_distance_tiers.sql}).
 * 판정 방식은 기존 여행 거리 4종과 동일하게 아직 미구현이다(아래 참고).
 *
 * <p><b>v18 변경 ② - 개별 여행(파티 없이 혼자 다녀온 여행) 인정.</b> TravelHeatmapService 와
 * 같은 이유로, 파티에 안 걸린(party_id NULL) 지역 태그 스냅도 여행 횟수·지역 판정에 넣는다
 * (자세한 배경은 그쪽 클래스 주석 참고). 시/군 단위 태그(예: "경주")는 {@link #normalizeRegion}
 * 이 상위 시/도("경북")로 접어서 합산한다 - 이 클래스의 "지역" 판정(8도 정복자, 광역시 특색
 * 등)은 전부 시/도·현 단위 전제라서다. 지도 드릴다운(mypage-heatmap.js)은 반대로 시/군
 * 단위를 그대로 보여줘야 해서 접지 않는다 - 같은 원본 데이터를 서로 다른 두 단위로 쓰는 셈이다.
 *
 * <p><b>v21 변경 - 지역 이름 표를 전부 {@link RegionCatalog} 로 옮겼다.</b> 예전에는 별칭표와
 * 시/군→시/도 표(경북만)를 이 클래스가 직접 들고 있어서, 다른 시/도의 시/군 이름이 들어오기
 * 시작하면 여기 표를 같이 늘려주지 않는 한 그 지역 판정이 조용히 새 나갔다. 이제 이름 계층은
 * region-tree.json 한 곳에만 있고 이 클래스는 물어보기만 한다.
 *
 * <p><b>판정 근거</b>는 완료된 파티(PartyStatus.completed) + 개별 여행이다. TravelHeatmapService
 * 와 같은 근거를 쓰되, 지도는 "지수"로 색을 칠하고 칭호는 "횟수"로 판정한다.
 *
 * <p><b>아직 판정하지 않는 12종</b> - 행은 있지만 근거 데이터가 없어 잠금 상태로 둔다.
 * <ul>
 *   <li>명예 OO인 5종(R_GS/R_JL/R_CC/R_GW/R_GG) — "비거주자" 판정에 집 주소 필요</li>
 *   <li>여행 거리 6종(D400~D40000) — 집 좌표 기준 누적 거리 필요</li>
 *   <li>야경 헌터(A_NIGHT) — 스냅에 '야경' 분류가 없음</li>
 * </ul>
 * users 에 home_lat/lng 가 생기면(⑤ 스키마) 여기에 판정만 추가하면 된다.
 *
 * <p>부여 시점은 마이페이지를 열 때다. 원래는 파티 완료 시점(PartyCompletionScheduler)에
 * 한 번만 확인하는 게 맞지만 그 스케줄러는 ⑤(허수연) 소유라 임의로 고치지 않았다.
 * syncTitles 는 멱등이라 호출부를 옮겨도 부작용이 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TitleService {

    /** 한 지역을 "특색 칭호" 수준으로 다녀왔다고 보는 횟수. */
    static final int REGION_MASTER_TRIPS = 5;

    /** 파티 개설/참여 칭호 기준 횟수. */
    static final int PARTY_ACTIVITY_COUNT = 10;

    /** 축제 마니아 - 축제 스타일 파티 완료 횟수. */
    static final int FESTIVAL_TRIPS = 5;

    /** 지도 수집가 - 다녀온 지역 수. */
    static final int COLLECTOR_REGIONS = 10;

    /** 파티 스타일 태그 중 '축제'. parties.style_tag 에 한글로 저장된다. */
    private static final String STYLE_FESTIVAL = "축제";

    /** 여행 횟수 칭호: 코드 -> 필요 횟수. 조건을 만족하면 아래 등급을 전부 함께 받는다. */
    private static final Map<String, Integer> TRIP_COUNT_TITLES = Map.of(
            "T1", 1, "T15", 15, "T30", 30, "T50", 50, "T80", 80, "T100", 100);

    /** 단일 지역 칭호: 코드 -> 지역명. 조건은 모두 REGION_MASTER_TRIPS 회 이상. */
    private static final Map<String, String> SINGLE_REGION_TITLES = Map.ofEntries(
            Map.entry("M_SEOUL", "서울"), Map.entry("M_BUSAN", "부산"),
            Map.entry("M_DAEGU", "대구"), Map.entry("M_INCHEON", "인천"),
            Map.entry("M_GWANGJU", "광주"), Map.entry("M_DAEJEON", "대전"),
            Map.entry("M_ULSAN", "울산"), Map.entry("M_SEJONG", "세종"),
            Map.entry("R_JEJU", "제주"));

    /**
     * 일본 권역 칭호: 코드 -> 권역 이름.
     *
     * <p>parties.region 에는 '오사카', '홋카이도' 처럼 현 단위 이름이 들어오는데 칭호는
     * 권역 단위라, 그 현이 어느 권역에 속하는지 알아야 한다.
     *
     * <p><b>[v21 변경]</b> 예전에는 권역별 현 목록을 여기 직접 적어뒀다(regions.json ·
     * mypage-heatmap.js 와 같은 내용의 세 번째 사본이었다). 이제 소속은
     * {@link RegionCatalog} 에게 묻고, 이 표에는 "어떤 칭호가 어떤 권역인지"라는 칭호
     * 도메인 지식만 남긴다.
     */
    private static final Map<String, String> JAPAN_AREA_TITLES = Map.of(
            "J_KANSAI", "간사이",
            "J_KANTO", "간토",
            "J_KYUSHU", "규슈",
            "J_HOKKAIDO", "홋카이도",
            "J_OKINAWA", "오키나와");

    /**
     * '8도 정복자'(R_ALL8) 판정용 국내 8개 권역.
     *
     * <p>※ 목업의 "전국 8개 권역"에 정확한 정의가 없어서 행정 구역을 상식적으로 묶었다.
     * 팀에서 다른 기준을 쓰기로 하면 이 목록만 고치면 된다.
     */
    private static final List<Set<String>> KOREA_AREAS = List.of(
            Set.of("서울", "인천", "경기"),
            Set.of("강원"),
            Set.of("대전", "세종", "충북", "충남"),
            Set.of("전북"),
            Set.of("광주", "전남"),
            Set.of("대구", "경북"),
            Set.of("부산", "울산", "경남"),
            Set.of("제주"));

    /** 매너온도 칭호: 코드 -> 필요 온도. */
    private static final Map<String, BigDecimal> MANNER_TITLES = Map.of(
            "MAN40", new BigDecimal("40"),
            "MAN45", new BigDecimal("45"),
            "MAN50", new BigDecimal("50"));

    /** 화면에 카테고리를 늘어놓는 순서. */
    private static final List<String> CATEGORY_ORDER = List.of(
            "여행 횟수", "국내 지역 다양성", "광역시 특색", "일본 권역",
            "매너온도", "파티 활동", "여행 거리", "액티비티");

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyRepository partyRepository;
    private final RegionCatalog regionCatalog;

    /**
     * 완료한 여행·파티 활동·매너온도로 칭호를 확인해 부여한다.
     *
     * <p><b>v19 변경</b> - parties/posts 를 직접 스캔하던 판정 근거를 "내 여행"(my_trips)
     * 목록으로 옮겼다. {@code trips} 는 MyPageController 가 MyTripService.listMine 으로
     * 이미 동기화·조회한 뒤, 그중 {@link net.datasa.tanoshimi.service.MyTripService#isCountable}
     * 로 걸러낸 "실제로 카운트할" 목록만 넘겨준다 - 파티 여행이라도 연결된 스냅이 한 장도
     * 없으면 여기 들어오지 않는다(2026-09-01 요청: 파티만 만들고 완료 처리해서 여행 기록을
     * 조작하는 것을 막기 위함). SOLO 여행은 등록 자체가 근거라 항상 포함된다.
     *
     * <p>이미 가진 칭호는 건너뛰므로 여러 번 호출해도 안전하다. 한 번 딴 칭호는 실적이
     * 줄어도 회수하지 않는다(수집 요소라 뺏기면 기분이 나쁘다).
     */
    @Transactional
    public void syncTitles(UserEntity user, List<MyTripEntity> trips) {
        Map<String, Integer> tripsByRegion = new HashMap<>();
        int festivalTrips = 0;
        for (MyTripEntity trip : trips) {
            String region = normalizeRegion(trip.getDestination());
            if (region != null) {
                tripsByRegion.merge(region, 1, Integer::sum);
            }
            if (trip.isParty() && trip.getParty() != null && STYLE_FESTIVAL.equals(trip.getParty().getStyleTag())) {
                festivalTrips++;
            }
        }

        Set<String> earned = new HashSet<>();
        int totalTrips = trips.size();
        int visitedRegions = tripsByRegion.size();

        // 여행 횟수 - 조건을 넘긴 등급을 모두 준다(누적 수집형이라 상위만 주면 하위가 비어 보인다).
        TRIP_COUNT_TITLES.forEach((code, need) -> {
            if (totalTrips >= need) {
                earned.add(code);
            }
        });

        // 광역시 특색 + 감귤 마니아
        SINGLE_REGION_TITLES.forEach((code, region) -> {
            if (tripsByRegion.getOrDefault(region, 0) >= REGION_MASTER_TRIPS) {
                earned.add(code);
            }
        });

        // 일본 권역 - 그 권역에 속한 현들의 방문 횟수 합계로 판정.
        // 어떤 현이 어느 권역인지는 RegionCatalog(region-tree.json)가 단독으로 안다.
        JAPAN_AREA_TITLES.forEach((code, areaName) -> {
            int areaTrips = 0;
            for (Map.Entry<String, Integer> visited : tripsByRegion.entrySet()) {
                if (areaName.equals(regionCatalog.areaOf(visited.getKey()))) {
                    areaTrips += visited.getValue();
                }
            }
            if (areaTrips >= REGION_MASTER_TRIPS) {
                earned.add(code);
            }
        });

        // 8도 정복자 - 8개 권역을 한 번씩이라도 다녀왔는가
        boolean allAreas = true;
        for (Set<String> area : KOREA_AREAS) {
            boolean visited = false;
            for (String region : area) {
                if (tripsByRegion.getOrDefault(region, 0) > 0) {
                    visited = true;
                    break;
                }
            }
            if (!visited) {
                allAreas = false;
                break;
            }
        }
        if (allAreas) {
            earned.add("R_ALL8");
        }

        // 매너온도
        BigDecimal mannerTemp = user.getMannerTemp();
        if (mannerTemp != null) {
            MANNER_TITLES.forEach((code, need) -> {
                if (mannerTemp.compareTo(need) >= 0) {
                    earned.add(code);
                }
            });
        }

        // 파티 활동
        if (partyRepository.countByOwner(user) >= PARTY_ACTIVITY_COUNT) {
            earned.add("P_HOST");
        }
        if (partyMemberRepository.countByUser(user) >= PARTY_ACTIVITY_COUNT) {
            earned.add("P_JOIN");
        }

        // 액티비티
        if (festivalTrips >= FESTIVAL_TRIPS) {
            earned.add("A_FEST");
        }
        if (visitedRegions >= COLLECTOR_REGIONS) {
            earned.add("A_MAP");
        }

        awardAll(user, earned);
    }

    /**
     * 새로 딴 칭호만 저장한다.
     *
     * <p>코드 하나씩 findByCode 로 조회하면 38번을 왕복하게 되므로, 칭호 목록과 보유
     * 목록을 각각 한 번만 읽고 차집합만 저장한다.
     */
    private void awardAll(UserEntity user, Set<String> earnedCodes) {
        if (earnedCodes.isEmpty()) {
            return;
        }
        Set<String> ownedCodes = new HashSet<>(ownedCodes(user));
        for (TitleEntity title : titleRepository.findAll()) {
            String code = title.getCode();
            if (earnedCodes.contains(code) && !ownedCodes.contains(code)) {
                userTitleRepository.save(new UserTitleEntity(user, title));
                log.info("칭호 부여: userId={}, title={}", user.getId(), code);
            }
        }
    }

    /**
     * my_trips.destination 표기를 칭호가 쓰는 판정 단위로 맞춘다 - 한국은 시/도, 일본은
     * 도도부현. 긴 표기("경상북도")는 짧게, 국내 시/군("여수")은 소속 시/도("전남")로 접는다.
     *
     * <p><b>[v21 변경]</b> 별칭표와 시/군→시/도 표를 이 클래스가 직접 들고 있었는데,
     * region-tree.json 을 단일 출처로 삼는 {@link RegionCatalog#toTitleRegion} 으로 옮겼다.
     * 예전에는 경북 시/군만 표에 있어서, 다른 시/도의 드릴다운이 생길 때마다 여기 표를
     * 같이 늘려주지 않으면 "8도 정복자"·"지도 수집가" 판정이 조용히 새 나갔다.
     */
    private String normalizeRegion(String raw) {
        return regionCatalog.toTitleRegion(raw);
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
     * 보유한 칭호 전체(최근 획득 순). 마이페이지 칭호 칩 목록용.
     *
     * <p>대표 칭호 지정(equipped) 기능은 user_titles 에 컬럼이 없어 아직 못 만든다 -
     * 스키마 변경은 ⑤(허수연) 조율 사항이라 협의 후 추가 예정. 지금은 latestTitle 을
     * 대표로 보여준다.
     */
    @Transactional(readOnly = true)
    public List<TitleEntity> ownedTitles(UserEntity user) {
        return userTitleRepository.findByUserOrderByEarnedAtDesc(user).stream()
                .map(UserTitleEntity::getTitle)
                .toList();
    }

    /** 보유한 칭호의 코드만. 화면에서 전체 목록과 대조할 때 쓴다. */
    @Transactional(readOnly = true)
    public List<String> ownedCodes(UserEntity user) {
        return userTitleRepository.findByUserOrderByEarnedAtDesc(user).stream()
                .map(userTitle -> userTitle.getTitle().getCode())
                .toList();
    }

    /**
     * 칭호 전체를 카테고리 순서대로. 칭호 관리 화면이 미획득분까지 보여주는 데 쓴다.
     *
     * <p>CATEGORY_ORDER 에 없는 카테고리(나중에 추가되거나 category 가 비어 있는 행)는
     * 목록 끝으로 보낸다 - 화면에서 사라지지 않게.
     *
     * <p>※ category 가 NULL 인 행을 반드시 견뎌야 한다. v17 마이그레이션을 아직 안 돌린
     * DB 에는 category 가 NULL 인 구 칭호가 남아 있고, 그 상태로 마이페이지를 열면
     * 이 정렬에서 터져 화면 전체가 500 이 된다(실제로 그렇게 터졌다).
     * {@code CATEGORY_ORDER} 는 {@code List.of(...)} 라 불변 리스트이고,
     * 불변 리스트의 {@code indexOf(null)} 은 -1 이 아니라 NPE 를 던진다.
     * 그래서 null 은 indexOf 에 넘기지 않고 미리 걸러낸다.
     */
    @Transactional(readOnly = true)
    public List<TitleEntity> allTitlesOrdered() {
        List<TitleEntity> titles = new ArrayList<>(titleRepository.findAll());
        titles.sort(Comparator
                .comparingInt((TitleEntity title) -> {
                    String category = title.getCategory();
                    int index = (category == null) ? -1 : CATEGORY_ORDER.indexOf(category);
                    return index < 0 ? CATEGORY_ORDER.size() : index;
                })
                .thenComparing(TitleEntity::getId));
        return titles;
    }
}
