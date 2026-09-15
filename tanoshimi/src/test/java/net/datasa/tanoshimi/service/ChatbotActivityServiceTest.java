package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.ActiveStatus;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import net.datasa.tanoshimi.domain.entity.PartyEntity;
import net.datasa.tanoshimi.domain.entity.PartyStatus;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.domain.entity.VenueType;
import net.datasa.tanoshimi.domain.dto.RecommendationDto;
import net.datasa.tanoshimi.repository.ActivityRepository;
import net.datasa.tanoshimi.repository.PartyMemberRepository;
import net.datasa.tanoshimi.util.GeminiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * [TNSM-49] 규칙 기반 추천 스텁 검증.
 * AI(GeminiClient) 호출이 아예 없거나(키워드 없음), 실패하거나(예외/파싱불가) 하는 상황에서
 * recommend()가 여전히 DB 액티비티 풀 기반의 "규칙 기반" 추천 목록을 안전하게 돌려주는지 확인한다.
 * (예전에는 키워드 문자열을 매칭해 하드코딩된 응답을 돌려주는 별도 분기로 검증했으나,
 *  운영 경로에 테스트 전용 백도어가 남는 문제가 있어 유닛 테스트로 대체했다.)
 *
 * [TNSM-18] 활동 이력 기반 추천(buildPastStyleTags) 검증도 함께 포함한다.
 */
@ExtendWith(MockitoExtension.class)
class ChatbotActivityServiceTest {
    
    @Mock private ActivityRepository activityRepository;
    @Mock private WeatherAdvisorService weatherAdvisorService;
    @Mock private GeminiClient geminiClient;
    @Mock private PartyMemberRepository partyMemberRepository;
    
    @InjectMocks
    private ChatbotActivityService chatbotActivityService;
    
    private ActivityEntity activity(long id, String title) {
        ActivityEntity a = ActivityEntity.builder()
                .title(title)
                .region("오사카")
                .durationMin(60)
                .priceKrw(10000)
                .priceJpy(1000)
                .description(title + " 설명")
                .build();
        a.cacheVenueType(VenueType.mixed); // 이미 판정된 상태로 만들어 AI 호출을 유발하지 않게 함
        setId(a, id);
        return a;
    }
    
    /** ActivityEntity.id는 @GeneratedValue라 테스트에서 리플렉션으로만 세팅 가능. */
    private void setId(ActivityEntity entity, long id) {
        try {
            var field = ActivityEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Test
    void recommend_키워드가_없으면_AI를_호출하지_않고_풀에서_바로_반환한다() {
        List<ActivityEntity> pool = List.of(activity(1L, "오사카성"), activity(2L, "도톤보리"));
        when(activityRepository.findByRegionAndStatus("오사카", ActiveStatus.active)).thenReturn(pool);
        
        List<RecommendationDto> result = chatbotActivityService.recommend("오사카", LocalDate.now(), null, "", false);
        
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getKind()).isEqualTo("recommend");
        verify(geminiClient, never()).ask(anyString());
    }
    
    @Test
    void recommend_AI_응답이_JSON이_아니면_규칙_기반_폴백으로_풀_목록을_반환한다() {
        List<ActivityEntity> pool = List.of(activity(1L, "오사카성"), activity(2L, "도톤보리"));
        when(activityRepository.findByRegionAndStatus("오사카", ActiveStatus.active)).thenReturn(pool);
        when(geminiClient.ask(anyString())).thenReturn("이건 JSON이 아니라 그냥 문장입니다.");
        
        List<RecommendationDto> result = chatbotActivityService.recommend("오사카", LocalDate.now(), "아무거나 추천해줘", "", false);
        
        assertThat(result).hasSize(2);
        assertThat(result).extracting(RecommendationDto::getTitle).containsExactly("오사카성", "도톤보리");
        assertThat(result).allMatch(r -> "recommend".equals(r.getKind()));
    }
    
    @Test
    void recommend_AI_호출이_예외를_던지면_규칙_기반_폴백으로_풀_목록을_반환한다() {
        List<ActivityEntity> pool = List.of(activity(1L, "오사카성"));
        when(activityRepository.findByRegionAndStatus("오사카", ActiveStatus.active)).thenReturn(pool);
        when(geminiClient.ask(anyString())).thenThrow(new RuntimeException("Gemini 타임아웃"));
        
        List<RecommendationDto> result = chatbotActivityService.recommend("오사카", LocalDate.now(), "아무거나 추천해줘", "", false);
        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("오사카성");
    }
    
    @Test
    void recommend_프롬프트에_지역_스타일태그_날짜가_반영된다() {
        // [TNSM-50] region/pastStyleTags/date가 실제로 AI 프롬프트에 들어가는지 검증
        List<ActivityEntity> pool = List.of(activity(1L, "오사카성"));
        when(activityRepository.findByRegionAndStatus("오사카", ActiveStatus.active)).thenReturn(pool);
        when(geminiClient.ask(anyString())).thenReturn("[]");
        
        LocalDate date = LocalDate.of(2026, 9, 20);
        chatbotActivityService.recommend("오사카", date, "커플 여행 코스 추천", "커플, 액티브", false);
        
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).ask(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        
        assertThat(prompt).contains("오사카");
        assertThat(prompt).contains("커플, 액티브");
        assertThat(prompt).contains("2026-09-20");
        assertThat(prompt).contains("커플 여행 코스 추천");
    }
    
    // ===================== [TNSM-18] 활동 이력 기반 추천 =====================
    
    @Test
    void 완료한_파티가_여러개면_스타일태그를_중복없이_모아서_반환한다() {
        UserEntity user = mock(UserEntity.class);
        PartyEntity p1 = PartyEntity.builder().title("파티1").styleTag("힐링").build();
        PartyEntity p2 = PartyEntity.builder().title("파티2").styleTag("액티비티").build();
        PartyEntity p3 = PartyEntity.builder().title("파티3").styleTag("힐링").build(); // 중복
        
        when(partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed))
                .thenReturn(List.of(p1, p2, p3));
        
        String result = chatbotActivityService.buildPastStyleTags(user);
        
        assertThat(result).isEqualTo("힐링, 액티비티"); // 중복 제거 확인
    }
    
    @Test
    void 완료한_파티가_없으면_null을_반환한다() {
        UserEntity user = mock(UserEntity.class);
        when(partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed))
                .thenReturn(List.of());
        
        String result = chatbotActivityService.buildPastStyleTags(user);
        
        assertThat(result).isNull();
    }
    
    @Test
    void styleTag가_비어있는_파티는_결과에서_제외된다() {
        UserEntity user = mock(UserEntity.class);
        PartyEntity p1 = PartyEntity.builder().title("파티1").styleTag(null).build();
        PartyEntity p2 = PartyEntity.builder().title("파티2").styleTag("  ").build();
        
        when(partyMemberRepository.findPartiesByUserAndStatus(user, PartyStatus.completed))
                .thenReturn(List.of(p1, p2));
        
        String result = chatbotActivityService.buildPastStyleTags(user);
        
        assertThat(result).isNull();
    }
}