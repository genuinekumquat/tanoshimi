package net.datasa.tanoshimi.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.companion.provider", havingValue = "mock", matchIfMissing = true)
public class MockGeminiClient implements GeminiClient {
	
	@Override
	public String ask(String prompt) {
		log.info("[MOCK] Gemini API 호출 (요청: {})", prompt);
		
		// 1. venue_type(실내/실외) 판정 프롬프트인 경우
		if (prompt.contains("INDOOR, OUTDOOR, or MIXED")) {
			if (prompt.contains("공원") || prompt.contains("산") || prompt.contains("해변") || prompt.contains("동조궁")) {
				return "OUTDOOR";
			}
			return "INDOOR";
		}
		// 2. 대화형 추천(태그 추출) 프롬프트인 경우
		else if (prompt.contains("Extract the single most important Korean search keyword")) {
			return "카페"; // 테스트용 고정 태그
		}
		// 3. [신규] recommend() 개편 후 다중 추천 프롬프트인 경우
		//    DB 목록에서 ID 몇 개를 뽑아 kind=recommend로, 하나는 kind=custom(가짜 신규 발굴)으로 섞어서 응답
		else if (prompt.contains("Return a JSON array of exactly 5 recommended schedule items")) {
			return "[" +
					"{\"kind\":\"recommend\",\"activityId\":1,\"title\":\"스미요시타이샤 하츠모데\",\"durationMin\":90}," +
					"{\"kind\":\"recommend\",\"activityId\":3,\"title\":\"우메다 공중정원 전망대\",\"durationMin\":60}," +
					"{\"kind\":\"custom\",\"activityId\":null,\"title\":\"[MOCK] 로컬 사진 명소\",\"durationMin\":45}," +
					"{\"kind\":\"recommend\",\"activityId\":2,\"title\":\"도톤보리 야경 산책\",\"durationMin\":60}," +
					"{\"kind\":\"custom\",\"activityId\":null,\"title\":\"[MOCK] 숨은 골목 카페\",\"durationMin\":40}" +
					"]";
		}
		
		return "UNKNOWN";
	}
}