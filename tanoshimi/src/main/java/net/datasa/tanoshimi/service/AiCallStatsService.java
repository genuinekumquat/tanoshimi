package net.datasa.tanoshimi.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * [TNSM-72 추가제안] 하루에 Gemini 호출이 어떤 결과로 끝났는지(성공/실패 사유별) 집계한다.
 * RealGeminiClient.ask() 한 곳에서만 record()를 호출하면 recommend/ai-validate/report/
 * judgeAndCacheVenueType 등 모든 AI 호출 경로가 자동으로 잡힌다.
 * 서버 메모리에만 쌓이는 가벼운 집계라 재시작하면 초기화된다(당일 모니터링 용도).
 */
@Service
public class AiCallStatsService {
	
	public enum Outcome {
		SUCCESS,        // 정상 응답
		NO_API_KEY,     // API 키 미설정
		EMPTY_RESPONSE, // candidates/parts가 비어있음(안전필터 등)
		RATE_LIMITED,   // 429
		OTHER_ERROR     // 그 외 예외(네트워크, 키 만료 등)
	}
	
	private final Map<LocalDate, Map<Outcome, AtomicInteger>> counts = new ConcurrentHashMap<>();
	
	public void record(Outcome outcome) {
		counts.computeIfAbsent(LocalDate.now(), d -> new ConcurrentHashMap<>())
				.computeIfAbsent(outcome, o -> new AtomicInteger())
				.incrementAndGet();
	}
	
	/** 오늘(자정 기준) 집계를 Outcome 이름 -> 횟수 Map으로 돌려준다. TOTAL 키에 합계도 같이 넣는다. */
	public Map<String, Integer> today() {
		Map<Outcome, AtomicInteger> todayMap = counts.getOrDefault(LocalDate.now(), Map.of());
		Map<String, Integer> result = new LinkedHashMap<>();
		int total = 0;
		for (Outcome o : Outcome.values()) {
			int v = todayMap.getOrDefault(o, new AtomicInteger()).get();
			result.put(o.name(), v);
			total += v;
		}
		result.put("TOTAL", total);
		return result;
	}
}