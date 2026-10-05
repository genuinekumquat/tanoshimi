package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.PlaceRegionView;
import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.repository.ActivityRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관광지 둘러보기(/recommendations). 계획표에 넣는 관광지(activities)를 지역별로 보여주고,
 * 각 지역에서 지금 모집 중인 파티로 바로 이어지게 한다 - 예전 "관광지 추천" 게시판은
 * 여행 스냅과 역할이 겹쳐 이 화면으로 바꿨다.
 */
@Service
@RequiredArgsConstructor
public class PlaceExploreService {

    private final ActivityRepository activityRepository;
    private final PartyService partyService;

    /** 노출 중인 관광지가 있는 지역 이름(가나다순) - 상단 지역 칩용. */
    @Transactional(readOnly = true)
    public List<String> regions() {
        return activityRepository.findByStatus(ActiveStatus.active).stream()
                .map(ActivityEntity::getRegion)
                .distinct()
                .sorted()
                .toList();
    }

    /** 노출 중인 관광지 수 - 화면 부제("일본 명소 N곳")용. */
    @Transactional(readOnly = true)
    public int totalPlaces() {
        return activityRepository.findByStatus(ActiveStatus.active).size();
    }

    /**
     * 지역별 관광지 + 모집 중 파티 수. region 이 비어 있으면 전체 지역, 있으면 그 지역 하나만.
     * 모집 중 파티 수는 파티 게시판 기본 목록(출발 전·모집중)과 같은 기준이라 버튼을 누르면 같은 수가 보인다.
     */
    @Transactional(readOnly = true)
    public List<PlaceRegionView> byRegion(String region) {
        List<ActivityEntity> places = (region == null || region.isBlank())
                ? activityRepository.findByStatus(ActiveStatus.active)
                : activityRepository.findByRegionAndStatus(region, ActiveStatus.active);

        Map<String, List<ActivityEntity>> grouped = places.stream()
                .sorted(Comparator.comparing(ActivityEntity::getRegion).thenComparing(ActivityEntity::getId))
                .collect(Collectors.groupingBy(ActivityEntity::getRegion, LinkedHashMap::new, Collectors.toList()));

        return grouped.entrySet().stream()
                .map(e -> new PlaceRegionView(e.getKey(), e.getValue(),
                        partyService.listBoard(e.getKey(), null, false).size()))
                .toList();
    }
}
