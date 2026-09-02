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
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class  SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final CustomOAuth2UserService oAuth2UserService;
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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/assets/**", "/vendor/**", "/favicon.ico", "/uploads/**", "/model/**").permitAll()
                        .requestMatchers("/api/companion/chat").permitAll()
                        .requestMatchers("/", "/login", "/logout", "/signup", "/signup/**",
                                "/api/auth/**", "/api/verification/**",
                                "/oauth2/**", "/login/oauth2/**", "/error", "/error/**").permitAll()
                        .requestMatchers("/party-board/create").authenticated()
                        .requestMatchers("/party-board/*/room").authenticated()
                        .requestMatchers("/board", "/board/**",
                                "/party-board", "/party-board/**", "/support", "/support/**").permitAll()
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
