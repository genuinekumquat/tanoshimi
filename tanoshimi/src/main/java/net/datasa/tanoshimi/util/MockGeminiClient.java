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
			// 4단계 날씨 테스트를 위해 특정 장소는 '실외(OUTDOOR)'로 판정하게 세팅
			if (prompt.contains("공원") || prompt.contains("산") || prompt.contains("해변") || prompt.contains("동조궁")) {
				return "OUTDOOR";
			}
			return "INDOOR";
		}
		// 2. 대화형 추천(태그 추출) 프롬프트인 경우
		else if (prompt.contains("Extract the single most important Korean search keyword")) {
			return "카페"; // 테스트용 고정 태그
		}
		
		return "UNKNOWN";
	}
}