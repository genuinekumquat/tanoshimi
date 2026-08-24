package net.datasa.tanoshimi.config;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetailsService;
import net.datasa.tanoshimi.auth.handler.LoginFailureHandler;
import net.datasa.tanoshimi.auth.handler.LoginSuccessHandler;
import net.datasa.tanoshimi.auth.oauth.CustomOAuth2UserService;
import net.datasa.tanoshimi.auth.oauth.OAuth2FailureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증/인가 설정.
 *
 * <p>새 화면을 추가하면 authorizeHttpRequests 에 URL 을 추가해 주세요.
 * 기본은 "로그인 필요" 이고, 공개할 화면만 permitAll 합니다.
 *
 * <p><b>파티 전용 페이지(/party/{id}/room/**)</b> 는 URL 패턴만으로는 "파티원인지"까지
 * 구분할 수 없어서 여기서는 "로그인만" 요구하고, 실제 멤버십 검사는
 * PartyRoomController 에서 PartyMemberRepository.existsByPartyAndUser() 로 한 번 더 확인합니다.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService oAuth2UserService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/assets/**", "/vendor/**", "/favicon.ico", "/uploads/**").permitAll()
                        .requestMatchers("/", "/login", "/logout", "/signup", "/signup/**",
                                "/api/auth/**", "/api/verification/**",
                                "/oauth2/**", "/login/oauth2/**", "/error", "/error/**").permitAll()
                        // 조회는 비로그인도 가능 (게시판, 패키지, 파티 목록 - 회원 전용은 글쓰기/신청/예약 시점에 막힘)
                        // 아래 두 규칙은 /party-board/** permitAll 와일드카드보다 먼저 와야 한다
                        // (Spring Security 는 먼저 매치되는 규칙을 쓰므로 더 구체적인 규칙을 위에 둔다)
                        .requestMatchers("/party-board/create").authenticated()
                        .requestMatchers("/party-board/*/room").authenticated()   // 파티 전용 페이지 - 비로그인 접근 차단
                        .requestMatchers("/api/packages/*/weather").permitAll()   // 날씨 미리보기는 읽기 전용이라 공개
                        .requestMatchers("/board", "/board/**", "/packages", "/packages/**",
                                "/party-board", "/party-board/**", "/support", "/support/**").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/ws/**").authenticated()   // 웹소켓 핸드셰이크도 로그인 필요
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll()
                )
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .userInfoEndpoint(userInfo -> userInfo.userService(oAuth2UserService))
                        .successHandler(loginSuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .sessionManagement(session -> session
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(1)
                        .maxSessionsPreventsLogin(false)
                )
                .exceptionHandling(handler -> handler.accessDeniedPage("/error/403"))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        // script-src 에 'unsafe-inline' 이 필요한 이유:
                        // Thymeleaf th:inline="javascript" 로 TOUR_ID, PARTY_ID, SCHEDULE_ID 등
                        // 서버 데이터를 화면 곳곳(패키지 상세, 파티방, 계획표, 쪽지방 등)에서 인라인
                        // <script> 블록으로 클라이언트에 넘기고 있다. 'self' 만 두면 그 블록들이
                        // 전부 브라우저에서 실행 차단되어 화면이 통째로 깨진다(TOUR_ID is not defined 등).
                        // TODO(다음 단계, 더 엄격하게 가고 싶으면): 인라인 스크립트를 없애고
                        // data-* 속성으로 값을 넘긴 뒤 외부 js 파일에서 dataset 으로 읽는 방식으로 리팩터링하면
                        // 'unsafe-inline' 없이도 동작한다. 지금은 학습/데모 규모라 우선 이렇게 푼다.
                        // font-src/style-src 에 외부 폰트 CDN을 허용해야 한다 - 안 그러면 CSS의
                        // @font-face(Pretendard, Jua 등)가 CSP 에 막혀 조용히 로드 실패하고 기본 폰트로만 보인다.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; img-src 'self' data: https:; " +
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; " +
                                "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com; " +
                                "script-src 'self' 'unsafe-inline'; " +
                                "connect-src 'self' ws: wss:; frame-ancestors 'none'"))
                )
                // CSRF 는 기본 활성. STOMP 웹소켓은 SockJS 핸드셰이크에 세션 쿠키를 그대로 쓰므로 별도 예외 불필요.
                // CSRF 는 기본 활성. 폼은 Thymeleaf 가 hidden 토큰을 자동 삽입하고, fetch 요청은 meta 태그의 토큰을
                // X-CSRF-TOKEN 헤더로 보낸다(/js/csrf.js 참고).
                // 단, /ws/** 는 SockJS 가 연결 초기에 순수 GET 웹소켓 업그레이드가 아니라
                // xhr-streaming/polling 같은 POST 기반 전송을 시도할 수 있어서 CSRF 검사에서 제외한다
                // (STOMP 쪽 인증은 세션 쿠키 + ChatWebSocketController 의 Principal 확인으로 대체).
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .userDetailsService(userDetailsService);

        return http.build();
    }
}
