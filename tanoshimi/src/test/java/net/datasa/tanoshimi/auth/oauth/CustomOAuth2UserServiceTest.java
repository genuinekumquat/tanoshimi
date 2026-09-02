package net.datasa.tanoshimi.auth.oauth;

import jakarta.servlet.http.HttpSession;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CustomOAuth2UserService 는 "성공 로그인"과 "추가정보 필요/이메일 중복/정지 계정" 을 전부
 * OAuth2AuthenticationException 으로 흘려보내는 특이한 제어 흐름을 쓴다(Spring Security 의
 * oauth2Login().failureHandler() 가 실제로는 "신규가입 유도" 같은 정상 분기까지 처리하게 됨).
 * 그래서 이 서비스는 "예외가 안 나는 성공 케이스"와 "에러 코드별로 다른 예외가 나는 케이스" 를
 * 전부 커버해야 실제로 안전하다.
 *
 * DefaultOAuth2UserService.loadUser() 는 실제 HTTP 호출을 하므로, delegate 를 생성자 주입으로
 * 바꿔서(이번 변경) mock 으로 대체했다 - 예전에는 필드에서 직접 new 해서 테스트 자체가 불가능했다.
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private DefaultOAuth2UserService delegate;
    @Mock
    private UserRepository userRepository;
    @Mock
    private HttpSession httpSession;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(delegate, userRepository, httpSession);
    }

    private OAuth2UserRequest googleRequest(Map<String, Object> attributes) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("google")
                .clientId("client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")
                .clientName("Google")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "dummy-token", Instant.now(), Instant.now().plusSeconds(3600));
        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                attributes, "sub");
        when(delegate.loadUser(any())).thenReturn(rawUser);
        return new OAuth2UserRequest(registration, accessToken);
    }

    private UserEntity newSocialUser(String provider, String socialId) {
        return UserEntity.createSocial("user@test.com", "yuzacha", "unusable-hash", "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR, provider, socialId);
    }

    @Test
    void 이미_연결된_활성_계정이면_CustomUserDetails를_반환한다() {
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-1", "email", "user@test.com", "name", "유자차"));
        UserEntity user = newSocialUser("google", "google-uid-1");
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-1")).thenReturn(Optional.of(user));

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) result).getEmail()).isEqualTo("user@test.com");
    }

    @Test
    void 이미_연결됐지만_정지된_계정이면_ACCOUNT_SUSPENDED_예외() {
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-1", "email", "user@test.com", "name", "유자차"));
        UserEntity user = newSocialUser("google", "google-uid-1");
        user.suspend(LocalDateTime.now().plusDays(7));
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-1")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.ACCOUNT_SUSPENDED);
    }

    @Test
    void 연결안된_소셜이지만_같은_이메일이_이미_가입돼있으면_EMAIL_ALREADY_USED_예외() {
        // 계정 탈취 방지 - 로컬로 먼저 가입한 이메일에 몰래 소셜 계정을 자동 연결시키지 않는다.
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-new", "email", "user@test.com", "name", "유자차"));
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-new")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.EMAIL_ALREADY_USED);
    }

    @Test
    void 완전히_새로운_사용자면_세션에_pending정보를_저장하고_SIGNUP_REQUIRED_예외() {
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-brand-new", "email", "new@test.com", "name", "유자차"));
        when(userRepository.findBySocialProviderAndSocialId("google", "google-uid-brand-new")).thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@test.com")).thenReturn(false);

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.SIGNUP_REQUIRED);

        org.mockito.ArgumentCaptor<PendingSocialSignup> captor = org.mockito.ArgumentCaptor.forClass(PendingSocialSignup.class);
        org.mockito.Mockito.verify(httpSession).setAttribute(org.mockito.ArgumentMatchers.eq(PendingSocialSignup.SESSION_KEY), captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("google");
        assertThat(captor.getValue().socialId()).isEqualTo("google-uid-brand-new");
        assertThat(captor.getValue().email()).isEqualTo("new@test.com");
    }

    @Test
    void 네이버_중첩응답도_동일한_흐름으로_동작한다() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("naver")
                .clientId("client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "dummy-token", Instant.now(), Instant.now().plusSeconds(3600));
        Map<String, Object> attributes = Map.of(
                "resultcode", "00",
                "response", Map.of("id", "naver-uid-1", "email", "user@test.com", "name", "유자차"));
        OAuth2User rawUser = new DefaultOAuth2User(
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER")),
                attributes, "response");
        when(delegate.loadUser(any())).thenReturn(rawUser);
        OAuth2UserRequest request = new OAuth2UserRequest(registration, accessToken);

        UserEntity user = newSocialUser("naver", "naver-uid-1");
        when(userRepository.findBySocialProviderAndSocialId("naver", "naver-uid-1")).thenReturn(Optional.of(user));

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
    }
}
