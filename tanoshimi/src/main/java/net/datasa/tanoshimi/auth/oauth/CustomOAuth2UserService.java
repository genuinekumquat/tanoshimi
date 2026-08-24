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

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final UserRepository userRepository;
    private final HttpSession httpSession;

    @Override
    @Transactional(readOnly = true)
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = delegate.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuthAttributes attrs = OAuthAttributes.of(registrationId, oAuth2User.getAttributes());

        // 소셜ID 로 이미 연결된 계정이면 바로 로그인
        Optional<UserEntity> linked = userRepository.findBySocialProviderAndSocialId(attrs.provider(), attrs.socialId());
        if (linked.isPresent()) {
            UserEntity user = linked.get();
            if (!user.isActive()) {
                throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.ACCOUNT_SUSPENDED), "정지 계정");
            }
            return new CustomUserDetails(user, oAuth2User.getAttributes());
        }

        // 이메일이 이미 로컬/다른 소셜로 가입돼 있으면 자동 연결하지 않고 막는다(계정 탈취 방지)
        if (attrs.email() != null && userRepository.existsByEmail(attrs.email())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.EMAIL_ALREADY_USED), "이메일 중복");
        }

        httpSession.setAttribute(PendingSocialSignup.SESSION_KEY,
                new PendingSocialSignup(attrs.provider(), attrs.socialId(), attrs.email(), attrs.name()));
        log.info("신규 소셜 사용자 -> 추가정보 입력 유도 provider={}", attrs.provider());

        throw new OAuth2AuthenticationException(new OAuth2Error(SocialErrorCodes.SIGNUP_REQUIRED), "추가정보 입력 필요");
    }
}
