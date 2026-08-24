package net.datasa.tanoshimi.auth;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 로그인 5회 연속 실패 시 잠금. 서버 메모리 기반(운영 규모 커지면 Redis 로 이전 필요). */
@Service
public class LoginAttemptService {

    @Value("${app.login.max-failures:5}")
    private int maxFailures;

    @Value("${app.login.lock-minutes:10}")
    private int lockMinutes;

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    public boolean isLocked(String email) {
        Attempt attempt = attempts.get(key(email));
        if (attempt == null || attempt.lockedUntil == null) return false;
        if (LocalDateTime.now().isAfter(attempt.lockedUntil)) {
            attempts.remove(key(email));
            return false;
        }
        return true;
    }

    public void recordFailure(String email) {
        attempts.compute(key(email), (k, prev) -> {
            Attempt attempt = (prev == null) ? new Attempt() : prev;
            attempt.count++;
            if (attempt.count >= maxFailures) {
                attempt.lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
                attempt.count = 0;
            }
            return attempt;
        });
    }

    public void reset(String email) { attempts.remove(key(email)); }
    public int lockMinutes() { return lockMinutes; }
    private String key(String email) { return email == null ? "" : email.trim().toLowerCase(); }

    private static class Attempt {
        private int count;
        private LocalDateTime lockedUntil;
    }
}
