package net.datasa.tanoshimi.auth.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.auth.LoginAttemptService;
import net.datasa.tanoshimi.auth.oauth.CustomOAuth2UserService;
import net.datasa.tanoshimi.domain.entity.PreferredLang;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final LoginAttemptService loginAttemptService;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // 로그인 성공한 사용자 정보 가져오기
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UserEntity user = userRepository.findByEmail(userDetails.getUsername()).orElse(null);

        // 로그인 성공 시 실패 카운트 초기화 (안 하면 이전 실패가 누적돼 정상 로그인 후에도 잠길 수 있음)
        loginAttemptService.reset(userDetails.getUsername());

        if (user != null) {
            // 1. HttpServletRequest에서 TANOSHIMI_LANG 쿠키 찾기
            String cookieLang = null;
            if (request.getCookies() != null) {
                cookieLang = Arrays.stream(request.getCookies())
                        .filter(c -> "TANOSHIMI_LANG".equals(c.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);
            }

            if (cookieLang != null) {
                // 2A. 쿠키가 존재하면 -> DB의 선호 언어(PreferredLang)를 업데이트
                try {
                    // 쿠키 값(ko, ja)을 Enum(KO, JA)으로 변환
                    PreferredLang langEnum = PreferredLang.valueOf(cookieLang.toUpperCase());
                    user.changePreferredLang(langEnum);

                    // @Transactional이 걸려있으므로 JPA의 Dirty Checking 기능이 작동해 save() 생략 가능하지만, 명시적으로 작성
                    userRepository.save(user);
                } catch (IllegalArgumentException e) {
                    // 쿠키에 이상한 값이 들어있으면 무시
                }
            } else if (user.getPreferredLang() != null) {
                // 2B. 쿠키는 없는데 DB에 이미 저장된 선호 언어가 있다면 -> 쿠키를 새로 구워서 Response에 추가
                Cookie langCookie = new Cookie("TANOSHIMI_LANG", user.getPreferredLang().name().toLowerCase());
                langCookie.setPath("/");
                langCookie.setMaxAge(365 * 24 * 60 * 60); // 1년 유지
                response.addCookie(langCookie);
            }
        }

        // 3. [social-link 신규] 방금 "계정 연동" 플로우로 로그인된 거면(CustomOAuth2UserService
        //    가 세운 1회성 플래그) 평소처럼 메인이 아니라 계정 관리 화면으로 보낸다. 이 플래그는
        //    쓰고 나면 바로 지운다 - 다음 로그인에 잘못 반응하지 않게.
        Object justLinked = request.getSession(false) != null
                ? request.getSession(false).getAttribute(CustomOAuth2UserService.LINK_SUCCESS_SESSION_KEY)
                : null;
        if (Boolean.TRUE.equals(justLinked)) {
            request.getSession(false).removeAttribute(CustomOAuth2UserService.LINK_SUCCESS_SESSION_KEY);
            setDefaultTargetUrl("/mypage/account?tab=social&linked=1");
        } else {
            // 처리가 끝나면 메인 화면("/")으로 리다이렉트
            setDefaultTargetUrl("/");
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}