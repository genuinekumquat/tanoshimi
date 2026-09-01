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
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
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
 * <p><b>판정 근거</b>는 완료된 파티(PartyStatus.completed)다. TravelHeatmapService 와 같은
 * 근거를 쓰되, 지도는 "지수"로 색을 칠하고 칭호는 "횟수"로 판정한다.
 *
 * <p><b>아직 판정하지 않는 10종</b> - 행은 있지만 근거 데이터가 없어 잠금 상태로 둔다.
 * <ul>
 *   <li>명예 OO인 5종(R_GS/R_JL/R_CC/R_GW/R_GG) — "비거주자" 판정에 집 주소 필요</li>
 *   <li>여행 거리 4종(D400~D10000) — 집 좌표 기준 누적 거리 필요</li>
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
     * 일본 권역 칭호: 코드 -> 그 권역에 속한 도도부현.
     *
     * <p>parties.region 에는 '오사카', '홋카이도' 처럼 현 단위 이름이 들어오는데 칭호는
     * 권역 단위라, 여기서 현을 권역으로 묶는다. 화면(mypage-heatmap.js)도 같은 계층을
     * 쓰지만 그쪽은 regions.json 의 지오메트리 기준이라 표가 따로 있다 - 지역 매핑을
     * 서버 한 곳으로 모으는 건 Phase 2 과제.
     */
    private static final Map<String, Set<String>> JAPAN_AREA_TITLES = Map.of(
            "J_KANSAI", Set.of("미에", "시가", "교토", "오사카", "효고", "나라", "와카야마"),
            "J_KANTO", Set.of("이바라키", "도치기", "군마", "사이타마", "지바", "도쿄", "가나가와"),
            "J_KYUSHU", Set.of("후쿠오카", "사가", "나가사키", "구마모토", "오이타", "미야자키", "가고시마"),
            "J_HOKKAIDO", Set.of("홋카이도"),
            "J_OKINAWA", Set.of("오키나와"));

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

    /**
     * parties.region 표기 흔들림 흡수. mypage-titles.js / mypage-heatmap.js 의
     * NAME_ALIASES 와 같은 표를 유지해야 한다.
     */
    private static final Map<String, String> REGION_ALIASES = Map.ofEntries(
            Map.entry("서울특별시", "서울"), Map.entry("경기도", "경기"), Map.entry("인천광역시", "인천"),
            Map.entry("강원특별자치도", "강원"), Map.entry("강원도", "강원"),
            Map.entry("충청북도", "충북"), Map.entry("충청남도", "충남"), Map.entry("세종특별자치시", "세종"),
            Map.entry("전라북도", "전북"), Map.entry("전북특별자치도", "전북"), Map.entry("전라남도", "전남"),
            Map.entry("경상북도", "경북"), Map.entry("경상남도", "경남"),
            Map.entry("대구광역시", "대구"), Map.entry("부산광역시", "부산"), Map.entry("광주광역시", "광주"),
            Map.entry("대전광역시", "대전"), Map.entry("울산광역시", "울산"),
            Map.entry("제주특별자치도", "제주"), Map.entry("제주도", "제주"));

    private final TitleRepository titleRepository;
    private final UserTitleRepository userTitleRepository;
    private final PartyMemberRepository partyMemberRepository;
    private final PartyRepository partyRepository;

    /**
     * 완료한 여행·파티 활동·매너온도로 칭호를 확인해 부여한다.
     *
     * <p>이미 가진 칭호는 건너뛰므로 여러 번 호출해도 안전하다. 한 번 딴 칭호는 실적이
     * 줄어도 회수하지 않는다(수집 요소라 뺏기면 기분이 나쁘다).
     */
    @Transactional
    public void syncTitles(UserEntity user) {
        List<PartyEntity> completed =
                partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed);

        Map<String, Integer> tripsByRegion = new HashMap<>();
        int festivalTrips = 0;
        for (PartyEntity party : completed) {
            String region = normalizeRegion(party.getRegion());
            if (region != null) {
                tripsByRegion.merge(region, 1, Integer::sum);
            }
            if (STYLE_FESTIVAL.equals(party.getStyleTag())) {
                festivalTrips++;
            }
        }

        Set<String> earned = new HashSet<>();
        int trips = completed.size();
        int visitedRegions = tripsByRegion.size();

        // 여행 횟수 - 조건을 넘긴 등급을 모두 준다(누적 수집형이라 상위만 주면 하위가 비어 보인다).
        TRIP_COUNT_TITLES.forEach((code, need) -> {
            if (trips >= need) {
                earned.add(code);
            }
        });

        // 광역시 특색 + 감귤 마니아
        SINGLE_REGION_TITLES.forEach((code, region) -> {
            if (tripsByRegion.getOrDefault(region, 0) >= REGION_MASTER_TRIPS) {
                earned.add(code);
            }
        });

        // 일본 권역 - 권역에 속한 현들의 방문 횟수 합계로 판정
        JAPAN_AREA_TITLES.forEach((code, prefectures) -> {
            int areaTrips = 0;
            for (String prefecture : prefectures) {
                areaTrips += tripsByRegion.getOrDefault(prefecture, 0);
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

    /** parties.region 표기를 지도/칭호가 쓰는 표준 지역명으로 맞춘다. */
    private static String normalizeRegion(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return REGION_ALIASES.getOrDefault(trimmed, trimmed);
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
     */
    @Transactional(readOnly = true)
    public List<TitleEntity> allTitlesOrdered() {
        List<TitleEntity> titles = new ArrayList<>(titleRepository.findAll());
        titles.sort(Comparator
                .comparingInt((TitleEntity title) -> {
                    int index = CATEGORY_ORDER.indexOf(title.getCategory());
                    return index < 0 ? CATEGORY_ORDER.size() : index;
                })
                .thenComparing(TitleEntity::getId));
        return titles;
    }
}
