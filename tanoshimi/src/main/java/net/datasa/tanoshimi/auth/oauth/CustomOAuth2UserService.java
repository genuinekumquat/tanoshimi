package net.datasa.tanoshimi.auth.oauth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.auth.CustomUserDetails;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.repository.UserRepository;
import java.util.Optional;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
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

    /**
     * [social-link 신규] "이미 로그인된 로컬 계정에 소셜 계정을 연동" 플로우가 시작될 때
     * (GET /mypage/account/social/link/{provider}) 대상 사용자 id 를 담아두는 세션 키.
     * 요청 파라미터/바디로는 절대 받지 않는다 - 오직 그 요청을 만든 세션의 인증 정보에서만
     * 채워지므로, 다른 사람 계정 id 를 흉내내서 넘길 방법이 없다. loadUser() 가 콜백 처리
     * 시점에 이 값을 읽고, 성공/실패 어느 쪽이든 즉시 제거한다(1회용).
     */
    public static final String LINK_TARGET_SESSION_KEY = "ACCOUNT_LINK_TARGET_USER_ID";

    /**
     * [social-link 신규] 연동에 성공했을 때만 1회성으로 세워두는 플래그. LoginSuccessHandler 가
     * 이 값을 보고 평소처럼 "/" 로 보내는 대신 /mypage/account?tab=social&amp;linked=1 로 보낸다.
     */
    public static final String LINK_SUCCESS_SESSION_KEY = "ACCOUNT_LINK_JUST_LINKED";

    // SecurityConfig 가 빈으로 등록해준 것을 주입받는다 - 예전엔 필드에서 직접 new 해서
    // 테스트에서 delegate.loadUser()(실제 HTTP 호출)를 mock으로 바꿔치기할 방법이 없었다.
    private final DefaultOAuth2UserService delegate;
    private final SocialLoginResolver socialLoginResolver;
    private final UserRepository userRepository;
    private final HttpSession httpSession;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        // [social-link 신규] 계정 연동 플로우 도중이면(세션에 대상 id 가 있으면) 완전히 다른
        // 분기로 처리하고 여기서 끝낸다 - 이 값이 없는 절대다수의 "그냥 로그인" 요청은
        // 이 if 블록을 타지 않고 일반 로그인/가입 경로(resolver)로 흘러간다.
        // "이미 연결된 계정이면 바로 로그인 / 정지 계정이면 예외" 판단은 SocialLoginResolver
        // 안으로 이미 옮겨져 있다(google/naver/line 공용) - 여기서 중복으로 볼 필요 없다.
        Object linkTarget = httpSession.getAttribute(LINK_TARGET_SESSION_KEY);
        if (linkTarget instanceof Long targetUserId) {
            return handleAccountLink(targetUserId, attrs, oAuth2User);
        }

        UserEntity user = socialLoginResolver.resolveOrRequireSignup(attrs);
        return new CustomUserDetails(user, oAuth2User.getAttributes());
    }

    /**
     * [social-link 신규] 로그인된 로컬 계정에 소셜 계정을 연동한다.
     *
     * <p>세션에 저장된 대상 id(targetUserId, 요청에서 받은 게 아니라 링크를 시작한 그 세션
     * 본인의 id)로 대상 계정을 찾고, 이 소셜 identity(provider+socialId)가:
     * <ul>
     *   <li>아무 계정에도 연결 안 돼 있으면 → 대상 계정에 연동하고 성공</li>
     *   <li>이미 대상 계정 본인에게 연결돼 있으면 → 재연동, 그냥 성공(no-op)</li>
     *   <li>이미 "다른" 계정에 연결돼 있으면 → 절대 그 다른 계정으로 로그인시키지 않고
     *       예외를 던져 실패시킨다. 여기서 예외를 던지면 스프링 시큐리티가 인증 실패 처리를
     *       하면서 현재 세션의 SecurityContext 를 지운다(AbstractAuthenticationProcessingFilter
     *       의 표준 동작) - 즉 원래 로그인해 있던 로컬 사용자가 로그아웃되는 부작용이 있다.
     *       이 프로젝트에는 이 필터 동작을 바꿀 안전한 확장점이 없어서(끼어들면 정상 로그인
     *       실패 처리까지 같이 망가뜨릴 위험), "다른 사람 계정으로 조용히 전환되는 것"보다는
     *       "로그아웃되고 에러 메시지로 다시 로그인 안내"가 훨씬 안전한 차선책이라고 판단했다.
     *       OAuth2FailureHandler 의 LINK_CONFLICT 분기 참고.</li>
     * </ul>
     */
    private OAuth2User handleAccountLink(Long targetUserId, OAuthAttributes attrs, OAuth2User oAuth2User) {
        httpSession.removeAttribute(LINK_TARGET_SESSION_KEY); // 1회용 - 성공/실패 관계없이 즉시 제거

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.LINK_CONFLICT), "연동 대상 계정을 찾을 수 없습니다."));

        Optional<UserEntity> existingLink = userRepository.findBySocialProviderAndSocialId(attrs.provider(), attrs.socialId());
        if (existingLink.isPresent() && !existingLink.get().getId().equals(target.getId())) {
            log.warn("소셜 연동 실패 - 이미 다른 계정에 연동된 소셜 identity. targetUserId={}, provider={}", targetUserId, attrs.provider());
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.LINK_CONFLICT), "이미 다른 계정에 연동된 소셜 계정입니다.");
        }

        if (existingLink.isEmpty()) {
            target.linkSocial(attrs.provider(), attrs.socialId());
            userRepository.saveAndFlush(target);
            log.info("소셜 연동 성공. targetUserId={}, provider={}", targetUserId, attrs.provider());
        } // existingLink 가 target 본인이면(재연동) 아무것도 바꿀 필요 없이 그대로 성공 처리

        httpSession.setAttribute(LINK_SUCCESS_SESSION_KEY, Boolean.TRUE);
        return new CustomUserDetails(target, oAuth2User.getAttributes());
    }

    /**
     * [social-link 신규] 로그인된 로컬 계정에 소셜 계정을 연동한다.
     *
     * <p>세션에 저장된 대상 id(targetUserId, 요청에서 받은 게 아니라 링크를 시작한 그 세션
     * 본인의 id)로 대상 계정을 찾고, 이 소셜 identity(provider+socialId)가:
     * <ul>
     *   <li>아무 계정에도 연결 안 돼 있으면 → 대상 계정에 연동하고 성공</li>
     *   <li>이미 대상 계정 본인에게 연결돼 있으면 → 재연동, 그냥 성공(no-op)</li>
     *   <li>이미 "다른" 계정에 연결돼 있으면 → 절대 그 다른 계정으로 로그인시키지 않고
     *       예외를 던져 실패시킨다. 여기서 예외를 던지면 스프링 시큐리티가 인증 실패 처리를
     *       하면서 현재 세션의 SecurityContext 를 지운다(AbstractAuthenticationProcessingFilter
     *       의 표준 동작) - 즉 원래 로그인해 있던 로컬 사용자가 로그아웃되는 부작용이 있다.
     *       이 프로젝트에는 이 필터 동작을 바꿀 안전한 확장점이 없어서(끼어들면 정상 로그인
     *       실패 처리까지 같이 망가뜨릴 위험), "다른 사람 계정으로 조용히 전환되는 것"보다는
     *       "로그아웃되고 에러 메시지로 다시 로그인 안내"가 훨씬 안전한 차선책이라고 판단했다.
     *       OAuth2FailureHandler 의 LINK_CONFLICT 분기 참고.</li>
     * </ul>
     */
    private OAuth2User handleAccountLink(Long targetUserId, OAuthAttributes attrs, OAuth2User oAuth2User) {
        httpSession.removeAttribute(LINK_TARGET_SESSION_KEY); // 1회용 - 성공/실패 관계없이 즉시 제거

        UserEntity target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.LINK_CONFLICT), "연동 대상 계정을 찾을 수 없습니다."));

        Optional<UserEntity> existingLink = userRepository.findBySocialProviderAndSocialId(attrs.provider(), attrs.socialId());
        if (existingLink.isPresent() && !existingLink.get().getId().equals(target.getId())) {
            log.warn("소셜 연동 실패 - 이미 다른 계정에 연동된 소셜 identity. targetUserId={}, provider={}", targetUserId, attrs.provider());
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.LINK_CONFLICT), "이미 다른 계정에 연동된 소셜 계정입니다.");
        }

        if (existingLink.isEmpty()) {
            target.linkSocial(attrs.provider(), attrs.socialId());
            userRepository.saveAndFlush(target);
            log.info("소셜 연동 성공. targetUserId={}, provider={}", targetUserId, attrs.provider());
        } // existingLink 가 target 본인이면(재연동) 아무것도 바꿀 필요 없이 그대로 성공 처리

        httpSession.setAttribute(LINK_SUCCESS_SESSION_KEY, Boolean.TRUE);
        return new CustomUserDetails(target, oAuth2User.getAttributes());
    }
}
