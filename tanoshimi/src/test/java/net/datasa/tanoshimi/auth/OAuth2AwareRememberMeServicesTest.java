package net.datasa.tanoshimi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 소셜 로그인은 리다이렉트 콜백이라 화면의 "자동 로그인" 토글 값을 실어 보낼 방법이 없어서,
 * 팀 결정(2026-09-09)대로 OAuth2 콜백 요청이면 파라미터 유무와 상관없이 항상 자동 로그인을
 * 요청한 것으로 간주하는지 검증한다. 폼 로그인은 기존처럼 파라미터 유무를 그대로 따라야 한다.
 */
class OAuth2AwareRememberMeServicesTest {

    private final OAuth2AwareRememberMeServices services = new OAuth2AwareRememberMeServices(
            "test-key", mock(UserDetailsService.class), mock(PersistentTokenRepository.class));

    @Test
    void OAuth2_콜백_요청이면_파라미터가_없어도_자동로그인_요청으로_본다() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");

        assertThat(services.rememberMeRequested(request, "remember-me")).isTrue();
    }

    @Test
    void 폼_로그인_요청은_체크박스_파라미터가_없으면_자동로그인_요청이_아니다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");

        assertThat(services.rememberMeRequested(request, "remember-me")).isFalse();
    }

    @Test
    void 폼_로그인_요청도_토글이_켜져_파라미터가_있으면_자동로그인_요청이다() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.addParameter("remember-me", "on");

        assertThat(services.rememberMeRequested(request, "remember-me")).isTrue();
    }
}
