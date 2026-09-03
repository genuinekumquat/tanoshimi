package net.datasa.tanoshimi.config;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.auth.CustomUserDetailsService;
import net.datasa.tanoshimi.auth.handler.LoginFailureHandler;
import net.datasa.tanoshimi.auth.handler.LoginSuccessHandler;
import net.datasa.tanoshimi.auth.oauth.CustomOAuth2UserService;
import net.datasa.tanoshimi.auth.oauth.CustomOidcUserService;
import net.datasa.tanoshimi.auth.oauth.OAuth2FailureHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenValidator;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class  SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService oAuth2UserService;
    private final CustomOidcUserService oidcUserService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    // CustomOAuth2UserService 가 생성자로 주입받아 테스트에서 mock으로 대체할 수 있게 빈으로 뺐다.
    // static 이어야 한다 - SecurityConfig 는 생성자에서 CustomOAuth2UserService 를 필요로 하는데,
    // 이 빈이 인스턴스 메서드면 SecurityConfig 인스턴스가 먼저 있어야 호출할 수 있어
    // "SecurityConfig -> CustomOAuth2UserService -> 이 빈 -> SecurityConfig" 순환참조가 생긴다.
    @Bean
    public static DefaultOAuth2UserService defaultOAuth2UserService() {
        return new DefaultOAuth2UserService();
    }

    // openid 스코프가 있는 line 로그인은 OidcUserService가 델리게이트로 필요하다 - 위와 같은 이유로 static.
    @Bean
    public static OidcUserService oidcUserServiceDelegate() {
        return new OidcUserService();
    }

    // LINE 채널의 id_token은 jwk-set-uri(RS256 공개키)가 아니라 채널 시크릿 기반 HS256으로
    // 서명되어 내려온다. 기본 OidcIdTokenDecoderFactory는 JWKS로만 검증기를 만들기 때문에
    // "Another algorithm expected, or no matching key(s) found" 로 항상 실패한다 - LINE만
    // 채널 시크릿으로 HS256 검증하도록 우회하고, 나머지(google)는 기본 동작을 그대로 쓴다.
    @Bean
    public JwtDecoderFactory<ClientRegistration> idTokenDecoderFactory() {
        OidcIdTokenDecoderFactory defaultFactory = new OidcIdTokenDecoderFactory();
        Map<String, JwtDecoder> lineDecoders = new ConcurrentHashMap<>();

        return clientRegistration -> {
            if (!"line".equals(clientRegistration.getRegistrationId())) {
                return defaultFactory.createDecoder(clientRegistration);
            }
            return lineDecoders.computeIfAbsent(clientRegistration.getRegistrationId(), id -> {
                SecretKeySpec secretKey = new SecretKeySpec(
                        clientRegistration.getClientSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                        .macAlgorithm(MacAlgorithm.HS256)
                        .build();
                OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(), new OidcIdTokenValidator(clientRegistration));
                decoder.setJwtValidator(validator);
                return decoder;
            });
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/assets/**", "/vendor/**", "/favicon.ico", "/uploads/**", "/model/**").permitAll()
                        .requestMatchers("/api/companion/chat").permitAll()
                        .requestMatchers("/", "/login", "/logout", "/signup", "/signup/**", "/find-password",
                                "/api/auth/**", "/api/verification/**",
                                "/oauth2/**", "/login/oauth2/**", "/error", "/error/**").permitAll()
                        .requestMatchers("/party-board/create").authenticated()
                        .requestMatchers("/party-board/*/room").authenticated()
                        .requestMatchers("/board", "/board/**",
                                "/party-board", "/party-board/**", "/support", "/support/**").permitAll()
                        // TNSM-52: 게시글 상세가 비로그인도 볼 수 있는 공개 페이지라, 그 화면이
                        // 클라이언트에서 fetch 하는 댓글 조회 API도 같이 공개해야 한다.
                        // 댓글 작성/삭제(POST/DELETE)는 이 규칙에 안 걸리므로 계속 인증이 필요하다.
                        .requestMatchers(HttpMethod.GET, "/api/posts/*/comments").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/ws/**").authenticated()
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
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(oAuth2UserService)
                                .oidcUserService(oidcUserService)
                        )
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
                        // Fix for CSP script blocking by allowing external CDNs in script-src
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; media-src 'self' https://cdn.jsdelivr.net; img-src 'self' data: https:; " +
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                                "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                                // 'unsafe-eval' 필요: pixi.js 가 초기화 시 셰이더 정밀도 체크를 new Function() 으로
                                // 수행해서(systemCheck), 이게 없으면 Live2D 캐릭터 렌더링이 "Current environment
                                // does not allow unsafe-eval" 에러로 조용히 실패한다(채팅 UI는 정상 동작해서 못 알아채기 쉬움).
                                "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com https://cubism.live2d.com; " +
                                // [신규] 여행 도우미 마스코트 위젯 - Live2D 모델(json/텍스처/모션)을 jsdelivr 에서
                                // fetch/XHR 로 불러오므로 connect-src 에도 같은 CDN 을 허용해야 한다.
                                "connect-src 'self' ws: wss: https://cdn.jsdelivr.net; frame-ancestors 'none'"))
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .userDetailsService(userDetailsService);

        return http.build();
    }
}
