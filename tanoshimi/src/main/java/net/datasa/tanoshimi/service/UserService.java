package net.datasa.tanoshimi.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.auth.oauth.PendingSocialSignup;
import net.datasa.tanoshimi.domain.dto.SignupRequest;
import net.datasa.tanoshimi.domain.dto.SocialSignupRequest;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import net.datasa.tanoshimi.util.UsernamePolicy;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PhoneVerificationService phoneVerificationService;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return email != null && !email.isBlank() && !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    /**
     * [vanity-url 신규] 회원가입 화면의 "중복확인" + 서버측 재검증 공용 - 형식/예약어(UsernamePolicy)
     * 를 먼저 걸러내고, 통과하면 DB 중복만 본다. signup()/signupSocial() 도 이 메서드를 그대로
     * 호출해서 클라이언트가 검증을 우회해도 서버가 다시 막는다.
     */
    @Transactional(readOnly = true)
    public boolean isUsernameAvailable(String rawUsername) {
        String normalized = UsernamePolicy.normalize(rawUsername);
        return UsernamePolicy.isAllowed(normalized) && !userRepository.existsByUsername(normalized);
    }

    private void requireAvailableUsername(String normalizedUsername) {
        if (!UsernamePolicy.isValidFormat(normalizedUsername)) {
            throw new BusinessException(ErrorCode.INVALID_USERNAME_FORMAT);
        }
        if (UsernamePolicy.isReserved(normalizedUsername)) {
            throw new BusinessException(ErrorCode.RESERVED_USERNAME);
        }
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw new BusinessException(ErrorCode.DUPLICATE_USERNAME);
        }
    }

    /**
     * [account-settings 신규] 회원정보 수정 등 민감한 동작 전 비밀번호 재확인.
     * 소셜 전용 계정은 UserEntity.createSocial() 이 알 수 없는 랜덤 문자열의 해시를 넣어두므로
     * (unusablePasswordHash 참고) 여기로 들어오면 항상 false 가 나온다 - 호출부(AccountSettings
     * 쪽 컨트롤러/서비스)가 isSocialAccount() 로 먼저 걸러서 소셜 계정에는 이 메서드를 아예
     * 타지 않게 해야 한다(정책은 AccountController 주석 참고).
     */
    @Transactional(readOnly = true)
    public boolean verifyPassword(UserEntity user, String rawPassword) {
        return rawPassword != null && passwordEncoder.matches(rawPassword, user.getPassword());
    }

    @Transactional
    public Long signup(SignupRequest rawRequest) {
        SignupRequest req = rawRequest.normalized();

        if (userRepository.existsByEmail(req.email())) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        if (userRepository.existsByPhone(req.phone())) throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        requireAvailableUsername(req.username());

        phoneVerificationService.consumeVerified(req.phone(), VerificationPurpose.signup);

        UserEntity user = UserEntity.createLocal(
                req.email(), req.username(), passwordEncoder.encode(req.password()), req.name(), req.phone(),
                Gender.valueOf(req.gender()), req.birthDate(), Nationality.valueOf(req.nationality()));

        // v17: 가입 직후 주던 NEWBIE 칭호가 없어졌다. 38종 체계에는 '가입만 하면 받는'
        // 칭호가 없고(가장 낮은 T1 도 '여행 1회'), 칭호는 마이페이지에서 실적을 보고 부여된다.
        return saveWithUniqueGuard(user);
    }

    @Transactional
    public UserEntity signupSocial(PendingSocialSignup pending, SocialSignupRequest rawRequest) {
        SocialSignupRequest req = rawRequest.normalized();
        String email = pending.email() != null ? pending.email() : req.email();
        if (email == null || email.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이메일을 입력해 주세요.");
        }
        if (userRepository.existsByEmail(email)) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        if (userRepository.existsByPhone(req.phone())) throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        requireAvailableUsername(req.username());

        phoneVerificationService.consumeVerified(req.phone(), VerificationPurpose.signup);

        UserEntity user = UserEntity.createSocial(
                email, req.username(), unusablePasswordHash(), req.name(), req.phone(),
                Gender.valueOf(req.gender()), req.birthDate(), Nationality.valueOf(req.nationality()),
                pending.provider(), pending.socialId());

        saveWithUniqueGuard(user);
        return user;
    }

    private Long saveWithUniqueGuard(UserEntity user) {
        try {
            return userRepository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            log.warn("가입 중 UNIQUE 충돌: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일, 아이디 또는 휴대폰 번호입니다.");
        }
    }

    private String unusablePasswordHash() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getUrlEncoder().encodeToString(bytes));
    }
}
