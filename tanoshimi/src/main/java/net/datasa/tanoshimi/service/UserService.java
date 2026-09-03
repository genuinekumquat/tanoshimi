package net.datasa.tanoshimi.service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
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
import net.datasa.tanoshimi.util.EmailSender;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TEMP_PASSWORD_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz";
    private static final String TEMP_PASSWORD_DIGITS = "23456789";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // v17(칭호 v17 개편)에서 TitleService 는 UserService 밖으로 빠졌다(가입만 하면 받는
    // 칭호가 없어져서) - main과 합치며 그 결정을 그대로 따르고, phoneVerificationService만
    // 이 브랜치의 목적대로 emailVerificationService 로 바꾼다.
    private final EmailVerificationService emailVerificationService;
    private final EmailSender emailSender;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return email != null && !email.isBlank() && !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    /** id 로 회원 조회. 없으면 USER_NOT_FOUND - "그 회원이 존재해야 의미가 있는" 화면(공개 프로필 등)용. */
    @Transactional(readOnly = true)
    public UserEntity getById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * [vanity-url] "/{username}" 프로필 주소용 조회 - username 형식이 유효할 때만 DB를 본다.
     * 형식이 안 맞거나 없는 회원이면 empty(예약어 여부는 조회를 막지 않는다 - "발급을 막는" 규칙일 뿐).
     */
    @Transactional(readOnly = true)
    public Optional<UserEntity> findByVanityUsername(String rawUsername) {
        String normalized = UsernamePolicy.normalize(rawUsername);
        if (!UsernamePolicy.isValidFormat(normalized)) {
            return Optional.empty();
        }
        return userRepository.findByUsername(normalized);
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

        // 알리고 등 SMS API가 사업자등록번호 없이는 실사용이 어려워 본인인증 채널을 이메일로 전환.
        emailVerificationService.consumeVerified(req.email(), VerificationPurpose.signup);

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

        // 소셜 로그인 자체가 이미 신뢰할 수 있는 인증 수단이라, 이메일을 provider가 줬든
        // (구글/네이버) 사용자가 직접 입력했든(LINE) 별도의 이메일 인증코드는 요구하지 않는다.
        // 로컬(이메일/비밀번호) 가입에만 이메일 인증이 필요하다 - signup() 참고.

        UserEntity user = UserEntity.createSocial(
                email, req.username(), unusablePasswordHash(), req.name(), req.phone(),
                Gender.valueOf(req.gender()), req.birthDate(), Nationality.valueOf(req.nationality()),
                pending.provider(), pending.socialId());

        saveWithUniqueGuard(user);
        return user;
    }

    /**
     * 비밀번호 재발급 - 이메일 입력 -> 임시 비밀번호를 생성해 즉시 password 에 반영하고
     * 이메일로 보낸다(팀 논의로 확정한 워크플로우: 발급 즉시 기존 비밀번호를 무효화).
     * 다음 로그인 때 강제로 비밀번호를 바꾸게 만든다. 소셜 전용 계정은 애초에 로그인
     * 불가능한 랜덤 해시만 갖고 있어 이 절차 대상이 아니다.
     */
    @Transactional
    public void issueTemporaryPassword(String rawEmail) {
        String email = rawEmail == null ? null : rawEmail.trim().toLowerCase();
        UserEntity user = userRepository.findByEmail(email).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isSocialAccount()) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NO_PASSWORD);
        }

        String tempPassword = generateTemporaryPassword();
        user.issueTemporaryPassword(passwordEncoder.encode(tempPassword));
        emailSender.sendTemporaryPassword(email, tempPassword);
    }

    /** 현재 비밀번호 확인 후 새 비밀번호로 교체한다. 자발적 변경과 강제 변경(임시 비밀번호 이후) 공용. */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    /** 10자 - 문자/숫자를 섞어 PasswordPolicy를 항상 만족시키고, 헷갈리기 쉬운 0/O/1/I/l 은 제외한다. */
    private String generateTemporaryPassword() {
        char[] chars = new char[10];
        for (int i = 0; i < 6; i++) chars[i] = TEMP_PASSWORD_LETTERS.charAt(RANDOM.nextInt(TEMP_PASSWORD_LETTERS.length()));
        for (int i = 6; i < 10; i++) chars[i] = TEMP_PASSWORD_DIGITS.charAt(RANDOM.nextInt(TEMP_PASSWORD_DIGITS.length()));
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp;
        }
        return new String(chars);
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
