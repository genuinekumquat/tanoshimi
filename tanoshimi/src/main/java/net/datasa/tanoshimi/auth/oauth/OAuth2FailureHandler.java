package net.datasa.tanoshimi.auth.oauth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OAuth2FailureHandler extends SimpleUrlAuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String code = (exception instanceof OAuth2AuthenticationException e) ? e.getError().getErrorCode() : "";
        String target = switch (code) {
            case SocialErrorCodes.SIGNUP_REQUIRED -> "/signup/social";
            case SocialErrorCodes.EMAIL_ALREADY_USED -> "/login?error=" + enc("이미 가입된 이메일입니다.");
            case SocialErrorCodes.ACCOUNT_SUSPENDED -> "/login?error=" + enc("정지된 계정입니다.");
            // [social-link 신규] 연동 실패(다른 계정에 이미 연동된 소셜) - loadUser 가 예외를 던지는
            // 시점에 스프링 시큐리티가 이미 SecurityContext 를 지워버려서(표준 필터 동작) 원래
            // 로그인해 있던 사용자도 로그아웃된 상태다. "다른 사람 계정으로 조용히 전환"보다는
            // 훨씬 안전하지만, 다시 로그인해야 한다는 걸 명확히 안내한다.
            case SocialErrorCodes.LINK_CONFLICT -> "/login?error=" + enc("이미 다른 계정에 연동된 소셜 계정이에요. 다시 로그인해 주세요.");
            default -> { log.warn("소셜 로그인 실패: {}", exception.getMessage()); yield "/login?error=" + enc("소셜 로그인에 실패했습니다."); }
        };
        getRedirectStrategy().sendRedirect(request, response, target);
    }
    private String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
