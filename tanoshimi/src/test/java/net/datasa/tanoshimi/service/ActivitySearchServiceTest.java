package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.dto.PlaceSearchResult;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.util.PlaceSearchClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivitySearchServiceTest {
	
	@Mock
	private PlaceSearchClient placeSearchClient;
	
	@Mock
	private ActivityRepository activityRepository;
	
	@InjectMocks
	private ActivitySearchService service;
	
	private PlaceSearchResult sampleResult() {
		return new PlaceSearchResult(
				"mock-osaka-castle", "osm", "오사카성",
				"오사카에서 검색된 장소", BigDecimal.valueOf(34.6873), BigDecimal.valueOf(135.5262), "ph1");
	}
	
	@Test
	void 신규_장소는_검색_후_저장된다() {
		PlaceSearchResult result = sampleResult();
		when(placeSearchClient.search("오사카성", "오사카")).thenReturn(List.of(result));
		when(activityRepository.findByExternalPlaceId("mock-osaka-castle")).thenReturn(Optional.empty());
		when(activityRepository.save(any(ActivityEntity.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));
		
		List<ActivityEntity> activities = service.searchAndCache("오사카성", "오사카");
		
		assertThat(activities).hasSize(1);
		assertThat(activities.get(0).getTitle()).isEqualTo("오사카성");
		verify(activityRepository, times(1)).save(any(ActivityEntity.class));
	}
	
	@Test
	void 이미_저장된_장소는_재조회_없이_재사용한다() {
		ActivityEntity existing = ActivityEntity.builder()
				.title("오사카성").region("오사카")
				.externalPlaceId("mock-osaka-castle")
				.build();
		when(placeSearchClient.search("오사카성", "오사카")).thenReturn(List.of(sampleResult()));
		when(activityRepository.findByExternalPlaceId("mock-osaka-castle")).thenReturn(Optional.of(existing));
		
		List<ActivityEntity> activities = service.searchAndCache("오사카성", "오사카");
		
		assertThat(activities).containsExactly(existing);
		verify(activityRepository, never()).save(any());
	}
	
	@Test
	void 동시_저장_경합이_발생하면_예외를_잡고_기존_데이터를_재사용한다() {
		ActivityEntity savedByOtherRequest = ActivityEntity.builder()
				.title("오사카성").region("오사카")
				.externalPlaceId("mock-osaka-castle")
				.build();
		when(placeSearchClient.search("오사카성", "오사카")).thenReturn(List.of(sampleResult()));
		when(activityRepository.findByExternalPlaceId("mock-osaka-castle"))
				.thenReturn(Optional.empty())
				.thenReturn(Optional.of(savedByOtherRequest));
		when(activityRepository.save(any(ActivityEntity.class)))
				.thenThrow(new DataIntegrityViolationException("unique constraint violated"));
		
		List<ActivityEntity> activities = service.searchAndCache("오사카성", "오사카");
		
		assertThat(activities).containsExactly(savedByOtherRequest);
		verify(activityRepository, times(2)).findByExternalPlaceId("mock-osaka-castle");
	}
	
	@Test
	void 검색어가_비어있으면_외부_API를_호출하지_않고_빈_목록을_반환한다() {
		List<ActivityEntity> activities = service.searchAndCache("  ", "오사카");
		
		assertThat(activities).isEmpty();
		verify(placeSearchClient, never()).search(any(), any());
	}
	
	@Test
	void 지역이_비어있으면_외부_API를_호출하지_않고_빈_목록을_반환한다() {
		List<ActivityEntity> activities = service.searchAndCache("오사카성", null);
		
		assertThat(activities).isEmpty();
		verify(placeSearchClient, never()).search(any(), any());
	}
}