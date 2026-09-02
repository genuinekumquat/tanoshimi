package net.datasa.tanoshimi.service;

import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.PlaceSearchResult;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.PlaceProvider;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.util.PlaceSearchClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * [v16 신규] 사용자가 장소 이름으로 검색하면 PlaceSearchClient 로 조회하고,
 * 결과를 ActivityEntity 로 저장(캐싱)한다.
 *
 * <p>같은 장소(external_place_id 동일)를 이미 저장해뒀으면 재조회 없이 기존 것을 재사용한다
 * (ActivityRepository.findByExternalPlaceId 참고). 동시에 여러 사용자가 같은 신규 장소를
 * 처음 검색하는 경합 상황(external_place_id UNIQUE 제약 위반)까지 방어한다 - 저장 실패 시
 * "누군가 먼저 저장했다"고 보고 다시 조회해서 그 결과를 재사용한다.
 *
 * <p>venue_type, style_tag(AI 판정), 가격 등은 이 서비스의 책임이 아니다 - 여기서는 장소검색
 * 결과를 저장만 하고, venue_type 판정은 ChatbotActivityService.recommend() 가 조회 시점에
 * 알아서 채운다(needsVenueTypeJudgement 기반).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ActivitySearchService {
	
	private final PlaceSearchClient placeSearchClient;
	private final ActivityRepository activityRepository;
	
	@Transactional
	public List<ActivityEntity> searchAndCache(String query, String region) {
		if (query == null || query.isBlank()) {
			log.warn("장소검색 요청에 검색어가 비어있어 무시함 (region={})", region);
			return List.of();
		}
		if (region == null || region.isBlank()) {
			log.warn("장소검색 요청에 지역이 비어있어 무시함 (query={})", query);
			return List.of();
		}
		
		List<PlaceSearchResult> results = placeSearchClient.search(query, region);
		List<ActivityEntity> activities = new ArrayList<>();
		
		for (PlaceSearchResult result : results) {
			ActivityEntity activity = findOrCreate(result, region);
			if (activity != null) {
				activities.add(activity);
			}
		}
		return activities;
	}
	
	/** 캐시 조회 -> 없으면 신규 저장. 동시 요청으로 인한 중복 저장 시도는 재조회로 흡수한다. */
	private ActivityEntity findOrCreate(PlaceSearchResult result, String region) {
		return activityRepository.findByExternalPlaceId(result.externalPlaceId())
				.orElseGet(() -> {
					try {
						ActivityEntity saved = activityRepository.save(toNewActivity(result, region));
						log.info("신규 장소 저장: {} ({})", saved.getTitle(), saved.getExternalPlaceId());
						return saved;
					} catch (DataIntegrityViolationException e) {
						// 동시에 다른 요청이 먼저 저장한 경우 - 그 결과를 그대로 재사용
						log.info("동시 저장 경합 감지, 기존 데이터 재사용: {}", result.externalPlaceId());
						return activityRepository.findByExternalPlaceId(result.externalPlaceId())
								.orElseThrow(() -> e);
					} catch (IllegalArgumentException e) {
						// provider 값이 PlaceProvider(google/osm)에 없는 값인 경우 - 이 결과만 건너뛰고 나머지는 살림
						log.warn("알 수 없는 provider 값 '{}' - 이 장소는 저장하지 않음: {}",
								result.provider(), result.title());
						return null;
					}
				});
	}
	
	private ActivityEntity toNewActivity(PlaceSearchResult result, String region) {
		return ActivityEntity.builder()
				.title(result.title())
				.region(region)
				.styleTag(null) // AI 판정은 별도 단계(ChatbotActivityService) - 여기서는 채우지 않음
				.durationMin(60) // 기본값 - 사용자가 계획표에서 직접 조정 가능
				.priceKrw(0)
				.priceJpy(0)
				.description(result.description())
				.thumbnailUrl(result.thumbnailUrl())
				.latitude(result.latitude())
				.longitude(result.longitude())
				.externalPlaceId(result.externalPlaceId())
				.placeProvider(PlaceProvider.valueOf(result.provider().toLowerCase()))
				.build();
	}
}