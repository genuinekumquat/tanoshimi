package net.datasa.tanoshimi.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanionChatRateLimiterTest {

    /** 테스트에서 시간을 직접 움직이기 위한 시계. */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-05T03:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void 분당_한도를_넘으면_COMPANION_RATE_LIMITED() {
        CompanionChatRateLimiter limiter = new CompanionChatRateLimiter(new MutableClock(), 3, 100);
        limiter.acquire(1L);
        limiter.acquire(1L);
        limiter.acquire(1L);

        assertThatThrownBy(() -> limiter.acquire(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANION_RATE_LIMITED);
    }

    @Test
    void 일분이_지나면_다시_호출할_수_있다() {
        MutableClock clock = new MutableClock();
        CompanionChatRateLimiter limiter = new CompanionChatRateLimiter(clock, 2, 100);
        limiter.acquire(1L);
        limiter.acquire(1L);

        clock.advance(Duration.ofSeconds(61));

        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void 하루_한도는_분당_한도와_별개로_적용된다() {
        MutableClock clock = new MutableClock();
        CompanionChatRateLimiter limiter = new CompanionChatRateLimiter(clock, 10, 3);
        for (int i = 0; i < 3; i++) {
            limiter.acquire(1L);
            clock.advance(Duration.ofMinutes(5));
        }

        assertThatThrownBy(() -> limiter.acquire(1L)).isInstanceOf(BusinessException.class);

        clock.advance(Duration.ofDays(1));
        assertThatCode(() -> limiter.acquire(1L)).doesNotThrowAnyException();
    }

    @Test
    void 사용자별로_따로_센다() {
        CompanionChatRateLimiter limiter = new CompanionChatRateLimiter(new MutableClock(), 1, 100);
        limiter.acquire(1L);

        assertThatCode(() -> limiter.acquire(2L)).doesNotThrowAnyException();
    }
}
