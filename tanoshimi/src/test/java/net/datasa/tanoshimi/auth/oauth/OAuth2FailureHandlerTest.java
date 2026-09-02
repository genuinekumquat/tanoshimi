package net.datasa.tanoshimi.auth.oauth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CustomOAuth2UserService 가 던지는 에러 코드별로 실제로 다른 곳(신규가입 유도 vs 로그인
 * 화면 에러메시지)으로 리다이렉트되는지 확인한다 - 에러 코드 하나를 잘못 매핑하면 사용자가
 * 엉뚱한 화면으로 튕기게 된다.
 */
@ExtendWith(MockitoExtension.class)
class OAuth2FailureHandlerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private OAuth2FailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2FailureHandler();
        when(request.getContextPath()).thenReturn("");
        stubPassthroughEncoding(response);
    }

    // DefaultRedirectStrategy가 내부적으로 response.encodeRedirectURL(url)을 거쳐 sendRedirect를
    // 호출하므로, mock 응답 그대로면 항상 null이 되어 실제 넘긴 target을 검증할 수 없다.
    private void stubPassthroughEncoding(HttpServletResponse res) {
        when(res.encodeRedirectURL(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private OAuth2AuthenticationException exceptionOf(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code), "test");
    }

    @Test
    void SIGNUP_REQUIRED는_소셜_추가정보_입력_화면으로_보낸다() throws Exception {
        handler.onAuthenticationFailure(request, response, exceptionOf(SocialErrorCodes.SIGNUP_REQUIRED));

        verify(response).sendRedirect("/signup/social");
    }

    @Test
    void EMAIL_ALREADY_USED는_에러메시지와_함께_로그인화면으로_보낸다() throws Exception {
        handler.onAuthenticationFailure(request, response, exceptionOf(SocialErrorCodes.EMAIL_ALREADY_USED));

        verify(response).sendRedirect(startsWith("/login?error="));
    }

    @Test
    void ACCOUNT_SUSPENDED는_에러메시지와_함께_로그인화면으로_보낸다() throws Exception {
        handler.onAuthenticationFailure(request, response, exceptionOf(SocialErrorCodes.ACCOUNT_SUSPENDED));

        verify(response).sendRedirect(startsWith("/login?error="));
    }

    @Test
    void 알수없는_에러코드나_일반_인증예외도_로그인화면_기본메시지로_보낸다() throws Exception {
        AuthenticationException generic = mock(AuthenticationException.class);
        when(generic.getMessage()).thenReturn("provider communication error");

        handler.onAuthenticationFailure(request, response, generic);

        verify(response).sendRedirect(startsWith("/login?error="));
    }

    @Test
    void 서로_다른_에러코드는_서로_다른_경로로_분기된다() throws Exception {
        // SIGNUP_REQUIRED 딱 하나만 /signup/social 로 가고, 나머지는 전부 /login 계열이어야 한다.
        handler.onAuthenticationFailure(request, response, exceptionOf(SocialErrorCodes.SIGNUP_REQUIRED));
        verify(response).sendRedirect(eq("/signup/social"));

        HttpServletResponse response2 = mock(HttpServletResponse.class);
        stubPassthroughEncoding(response2);
        handler.onAuthenticationFailure(request, response2, exceptionOf(SocialErrorCodes.EMAIL_ALREADY_USED));
        verify(response2).sendRedirect(any(String.class));
        verify(response2, org.mockito.Mockito.never()).sendRedirect("/signup/social");
    }
}
