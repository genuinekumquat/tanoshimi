package net.datasa.tanoshimi.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 타미 챗봇(/api/companion/chat) 사용자별 호출 제한.
 *
 * <p>호출 한 번이 곧 외부 AI(Gemini) 요금이라, 제한이 없으면 스크립트로 팀 키의 크레딧·호출 한도를
 * 순식간에 소진시킬 수 있다. AI 추천 크레딧(AiCreditService)과 달리 대화는 짧게 여러 번 주고받는
 * 게 정상이라 크레딧을 깎지 않고, "1분에 N번 / 하루 M번" 슬라이딩 윈도로만 막는다.
 * 서버 메모리에만 두므로 재시작하면 초기화된다(단일 서버 데모 환경 기준).
 */
@Component
public class CompanionChatRateLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Duration DAY = Duration.ofDays(1);

    private final Map<Long, Deque<Instant>> callsByUser = new ConcurrentHashMap<>();
    private final Clock clock;
    private final int perMinute;
    private final int perDay;

    @Autowired
    public CompanionChatRateLimiter(@Value("${app.companion.rate-limit.per-minute:10}") int perMinute,
                                    @Value("${app.companion.rate-limit.per-day:200}") int perDay) {
        this(Clock.systemDefaultZone(), perMinute, perDay);
    }

    CompanionChatRateLimiter(Clock clock, int perMinute, int perDay) {
        this.clock = clock;
        this.perMinute = perMinute;
        this.perDay = perDay;
    }

    /** 한도 안이면 이번 호출을 기록하고, 넘었으면 COMPANION_RATE_LIMITED 예외. */
    public void acquire(Long userId) {
        Instant now = clock.instant();
        Deque<Instant> calls = callsByUser.computeIfAbsent(userId, id -> new ArrayDeque<>());
        synchronized (calls) {
            while (!calls.isEmpty() && !calls.peekFirst().isAfter(now.minus(DAY))) {
                calls.pollFirst();
            }
            long lastMinute = calls.stream().filter(t -> t.isAfter(now.minus(MINUTE))).count();
            if (lastMinute >= perMinute || calls.size() >= perDay) {
                throw new BusinessException(ErrorCode.COMPANION_RATE_LIMITED);
            }
            calls.addLast(now);
        }
    }
}
