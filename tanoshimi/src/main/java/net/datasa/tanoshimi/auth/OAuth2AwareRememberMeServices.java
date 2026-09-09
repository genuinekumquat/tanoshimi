package net.datasa.tanoshimi.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

/**
 * 일반 로그인은 화면의 "자동 로그인" 토글(remember-me 파라미터)로 사용자가 직접 선택하지만,
 * 소셜 로그인(OAuth2)은 리다이렉트 콜백이라 그 파라미터를 실어 보낼 방법이 없다. 팀 결정
 * (2026-09-09) - 소셜 로그인은 한 번 시도하면 항상 자동 로그인되게 한다. OAuth2 콜백
 * URL(/login/oauth2/code/{registrationId}) 요청이면 무조건 "자동 로그인 요청됨"으로
 * 간주하고, 그 외(폼 로그인)는 기존처럼 remember-me 파라미터 유무를 본다.
 */
public class OAuth2AwareRememberMeServices extends PersistentTokenBasedRememberMeServices {

    private static final String OAUTH2_CALLBACK_PREFIX = "/login/oauth2/code/";

    public OAuth2AwareRememberMeServices(String key, UserDetailsService userDetailsService,
                                          PersistentTokenRepository tokenRepository) {
        super(key, userDetailsService, tokenRepository);
    }

    @Override
    protected boolean rememberMeRequested(HttpServletRequest request, String parameter) {
        if (request.getRequestURI().startsWith(request.getContextPath() + OAUTH2_CALLBACK_PREFIX)) {
            return true;
        }
        return super.rememberMeRequested(request, parameter);
    }
}
