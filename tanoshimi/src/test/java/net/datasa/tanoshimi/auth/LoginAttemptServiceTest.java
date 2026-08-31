package net.datasa.tanoshimi.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class LoginAttemptServiceTest {

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService();
        ReflectionTestUtils.setField(service, "maxFailures", 3);
        ReflectionTestUtils.setField(service, "lockMinutes", 10);
    }

    @Test
    void 초기_상태는_잠기지_않는다() {
        assertThat(service.isLocked("user@test.com")).isFalse();
    }

    @Test
    void 최대_실패_횟수_미만이면_잠기지_않는다() {
        service.recordFailure("user@test.com");
        service.recordFailure("user@test.com");

        assertThat(service.isLocked("user@test.com")).isFalse();
    }

    @Test
    void 최대_실패_횟수에_도달하면_잠긴다() {
        service.recordFailure("user@test.com");
        service.recordFailure("user@test.com");
        service.recordFailure("user@test.com");

        assertThat(service.isLocked("user@test.com")).isTrue();
    }

    @Test
    void 이메일은_대소문자와_공백을_구분하지_않는다() {
        service.recordFailure("  User@Test.com ");
        service.recordFailure("user@test.com");
        service.recordFailure("USER@TEST.COM");

        assertThat(service.isLocked("user@test.com")).isTrue();
    }

    @Test
    void reset하면_잠금과_누적_카운트가_모두_풀린다() {
        service.recordFailure("user@test.com");
        service.recordFailure("user@test.com");
        service.recordFailure("user@test.com");
        assertThat(service.isLocked("user@test.com")).isTrue();

        service.reset("user@test.com");
        assertThat(service.isLocked("user@test.com")).isFalse();

        // reset 이후에는 카운트도 0부터 다시 시작해야 한다 (누적된 실패가 남아있으면 안 됨)
        service.recordFailure("user@test.com");
        service.recordFailure("user@test.com");
        assertThat(service.isLocked("user@test.com")).isFalse();
    }

    @Test
    void 다른_이메일의_실패는_서로_영향을_주지_않는다() {
        service.recordFailure("a@test.com");
        service.recordFailure("a@test.com");
        service.recordFailure("a@test.com");

        assertThat(service.isLocked("a@test.com")).isTrue();
        assertThat(service.isLocked("b@test.com")).isFalse();
    }
}
