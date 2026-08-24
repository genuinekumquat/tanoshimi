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
    private final TitleService titleService;

    @Transactional(readOnly = true)
    public boolean isEmailAvailable(String email) {
        return email != null && !email.isBlank() && !userRepository.existsByEmail(email.trim().toLowerCase());
    }

    @Transactional
    public Long signup(SignupRequest rawRequest) {
        SignupRequest req = rawRequest.normalized();

        if (userRepository.existsByEmail(req.email())) throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        if (userRepository.existsByPhone(req.phone())) throw new BusinessException(ErrorCode.DUPLICATE_PHONE);

        phoneVerificationService.consumeVerified(req.phone(), VerificationPurpose.signup);

        UserEntity user = UserEntity.createLocal(
                req.email(), passwordEncoder.encode(req.password()), req.name(), req.phone(),
                Gender.valueOf(req.gender()), req.birthDate(), Nationality.valueOf(req.nationality()));

        Long userId = saveWithUniqueGuard(user);
        titleService.awardNewbie(user);
        return userId;
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

        phoneVerificationService.consumeVerified(req.phone(), VerificationPurpose.signup);

        UserEntity user = UserEntity.createSocial(
                email, unusablePasswordHash(), req.name(), req.phone(),
                Gender.valueOf(req.gender()), req.birthDate(), Nationality.valueOf(req.nationality()),
                pending.provider(), pending.socialId());

        saveWithUniqueGuard(user);
        titleService.awardNewbie(user);
        return user;
    }

    private Long saveWithUniqueGuard(UserEntity user) {
        try {
            return userRepository.saveAndFlush(user).getId();
        } catch (DataIntegrityViolationException e) {
            log.warn("가입 중 UNIQUE 충돌: {}", e.getMessage());
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "이미 가입된 이메일 또는 휴대폰 번호입니다.");
        }
    }

    private String unusablePasswordHash() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getUrlEncoder().encodeToString(bytes));
    }
}
