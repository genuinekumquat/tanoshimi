package net.datasa.tanoshimi.auth.oauth;

import net.datasa.tanoshimi.auth.CustomOidcUser;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

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
 * line처럼 scope에 openid가 있는 로그인 전용 경로 - Spring Security가 이런 요청을 OIDC로
 * 인식해서 CustomOAuth2UserService가 아니라 이쪽을 탄다(SecurityConfig.oidcUserService() 참고).
 * CustomOAuth2UserService와 마찬가지로 SocialLoginResolver에게 판단을 위임하고, 성공 시
 * idToken/userInfo를 보존한 CustomOidcUser로 감싸는지가 핵심이다 - CustomOidcUser가
 * CustomUserDetails를 상속하므로 LoginSuccessHandler 등의 기존 캐스팅도 그대로 통해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock
    private OidcUserService delegate;
    @Mock
    private SocialLoginResolver socialLoginResolver;

    private CustomOidcUserService service;

    @BeforeEach
    void setUp() {
        service = new CustomOidcUserService(delegate, socialLoginResolver);
    }

    private OidcUserRequest lineRequest(Map<String, Object> claims) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("line")
                .clientId("client-id")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .authorizationUri("https://access.line.me/oauth2/v2.1/authorize")
                .tokenUri("https://api.line.me/oauth2/v2.1/token")
                .userInfoUri("https://api.line.me/oauth2/v2.1/userinfo")
                .userNameAttributeName("sub")
                .clientName("LINE")
                .scope("openid", "profile", "email")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "dummy-token", Instant.now(), Instant.now().plusSeconds(3600));
        OidcIdToken idToken = new OidcIdToken("dummy-id-token", Instant.now(), Instant.now().plusSeconds(3600), claims);
        OidcUser rawUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken, "sub");
        when(delegate.loadUser(any())).thenReturn(rawUser);
        return new OidcUserRequest(registration, accessToken, idToken);
    }

    @Test
    void 라인_속성을_변환해서_resolver에_넘기고_CustomOidcUser로_감싼다() {
        OidcUserRequest request = lineRequest(Map.of(
                IdTokenClaimNames.SUB, "line-uid-1", "name", "유자차", "email", "user@test.com"));
        UserEntity user = UserEntity.createSocial("user@test.com", "yuzacha", "unusable-hash", "유자차", "01011112222",
                Gender.female, LocalDate.of(1998, 5, 14), Nationality.KR, "line", "line-uid-1");
        when(socialLoginResolver.resolveOrRequireSignup(any())).thenReturn(user);

        OidcUser result = service.loadUser(request);

        assertThat(result).isInstanceOf(CustomOidcUser.class);
        assertThat(result.getEmail()).isEqualTo("user@test.com");
        // CustomOidcUser는 CustomUserDetails를 상속하므로 idToken도 그대로 보존돼야 한다.
        assertThat(result.getIdToken().getTokenValue()).isEqualTo("dummy-id-token");

        ArgumentCaptor<OAuthAttributes> captor = ArgumentCaptor.forClass(OAuthAttributes.class);
        verify(socialLoginResolver).resolveOrRequireSignup(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("line");
        assertThat(captor.getValue().socialId()).isEqualTo("line-uid-1");
        assertThat(captor.getValue().email()).isEqualTo("user@test.com");
    }

    @Test
    void 이메일_스코프_심사_전이면_email_null로_resolver에_넘긴다() {
        // LINE 채널이 이메일 권한 심사를 통과하지 못했으면 email claim 자체가 안 내려온다.
        OidcUserRequest request = lineRequest(Map.of(IdTokenClaimNames.SUB, "line-uid-2", "name", "유자차"));
        when(socialLoginResolver.resolveOrRequireSignup(any()))
                .thenThrow(new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.SIGNUP_REQUIRED), "추가정보 입력 필요"));

        assertThatThrownBy(() -> service.loadUser(request))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(SocialErrorCodes.SIGNUP_REQUIRED);

        ArgumentCaptor<OAuthAttributes> captor = ArgumentCaptor.forClass(OAuthAttributes.class);
        verify(socialLoginResolver).resolveOrRequireSignup(captor.capture());
        assertThat(captor.getValue().provider()).isEqualTo("line");
        assertThat(captor.getValue().email()).isNull();
    }
}
