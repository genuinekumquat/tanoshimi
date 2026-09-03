package net.datasa.tanoshimi.auth.oauth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * google/naver 처럼 openid 스코프가 없는(=일반 OAuth2) 소셜 로그인 전용.
 * openid 스코프가 있는 line은 Spring Security가 OIDC 로그인으로 취급해서 이 서비스가 아니라
 * CustomOidcUserService 를 탄다 - SecurityConfig 의 oidcUserService()로 별도 연결돼 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    // SecurityConfig 가 빈으로 등록해준 것을 주입받는다 - 예전엔 필드에서 직접 new 해서
    // 테스트에서 delegate.loadUser()(실제 HTTP 호출)를 mock으로 바꿔치기할 방법이 없었다.
    private final DefaultOAuth2UserService delegate;
    private final SocialLoginResolver socialLoginResolver;

    @Override
    @Transactional(readOnly = true)
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        UserEntity user = socialLoginResolver.resolveOrRequireSignup(attrs);
        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }
}
