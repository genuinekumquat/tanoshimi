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

    // CustomOAuth2UserService 가 생성자로 주입받아 테스트에서 mock으로 대체할 수 있게 빈으로 뺐다.
    @Bean
    public DefaultOAuth2UserService defaultOAuth2UserService() {
        return new DefaultOAuth2UserService();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/assets/**", "/vendor/**", "/favicon.ico", "/uploads/**").permitAll()
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
                                "default-src 'self'; img-src 'self' data: https:; " +
                                "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com https://cdnjs.cloudflare.com; " +
                                "font-src 'self' https://cdn.jsdelivr.net https://fonts.gstatic.com https://cdnjs.cloudflare.com; " +
                                "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; " +
                                "connect-src 'self' ws: wss:; frame-ancestors 'none'"))
                )
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .userDetailsService(userDetailsService);

        return http.build();
    }
}
