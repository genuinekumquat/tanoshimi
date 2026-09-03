package net.datasa.tanoshimi.auth.oauth;

import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * google/naver(=openid 스코프 없는 일반 OAuth2) 전용. 계정 연결/신규가입 판단 자체는
 * SocialLoginResolver 로 옮겨졌으니(SocialLoginResolverTest 참고), 여기서는 delegate가 준
 * 응답을 OAuthAttributes로 정확히 변환해서 resolver에 넘기고, 그 결과를 CustomUserDetails로
 * 감싸서 돌려주는지 + resolver가 던지는 예외를 그대로 전파하는지만 본다.
 */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    @Mock
    private DefaultOAuth2UserService delegate;
    @Mock
    private SocialLoginResolver socialLoginResolver;

    private CustomOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOAuth2UserService(delegate, socialLoginResolver);
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
        return UserEntity.createSocial("user@test.com", "unusable-hash", "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR, provider, socialId);
    }

    @Test
    void 구글_속성을_변환해서_resolver에_넘기고_CustomUserDetails로_감싼다() {
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-1", "email", "user@test.com", "name", "유자차"));
        UserEntity user = newSocialUser("google", "google-uid-1");
        when(socialLoginResolver.resolveOrRequireSignup(any())).thenReturn(user);

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        assertThat(((CustomUserDetails) result).getEmail()).isEqualTo("user@test.com");

        ArgumentCaptor<OAuthAttributes> captor = ArgumentCaptor.forClass(OAuthAttributes.class);
        verify(socialLoginResolver).resolveOrRequireSignup(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("google");
        assertThat(captor.getValue().socialId()).isEqualTo("google-uid-1");
        assertThat(captor.getValue().email()).isEqualTo("user@test.com");
    }

    @Test
    void 네이버_중첩응답도_정상적으로_변환해서_resolver에_넘긴다() {
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
        when(socialLoginResolver.resolveOrRequireSignup(any())).thenReturn(user);

        OAuth2User result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomUserDetails.class);
        ArgumentCaptor<OAuthAttributes> captor = ArgumentCaptor.forClass(OAuthAttributes.class);
        verify(socialLoginResolver).resolveOrRequireSignup(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("naver");
        assertThat(captor.getValue().socialId()).isEqualTo("naver-uid-1");
        assertThat(captor.getValue().email()).isEqualTo("user@test.com");
    }

    @Test
    void resolver가_던지는_예외를_그대로_전파한다() {
        // SIGNUP_REQUIRED/EMAIL_ALREADY_USED/ACCOUNT_SUSPENDED 같은 resolver의 제어 흐름은
        // 그대로 흘러나와야 OAuth2FailureHandler가 화면 분기를 정상적으로 처리할 수 있다.
        OAuth2UserRequest request = googleRequest(Map.of("sub", "google-uid-2", "email", "new@test.com", "name", "유자차"));
        when(socialLoginResolver.resolveOrRequireSignup(any()))
                .thenThrow(new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.SIGNUP_REQUIRED), "추가정보 입력 필요"));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.SIGNUP_REQUIRED);
    }
}
