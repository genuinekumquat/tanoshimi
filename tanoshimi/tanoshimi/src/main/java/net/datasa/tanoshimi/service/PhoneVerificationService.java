package net.datasa.tanoshimi.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.PhoneVerificationEntity;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.PhoneVerificationRepository;
import net.datasa.tanoshimi.util.SmsSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알리고 SMS 인증번호 발급/검증. 인증번호는 해시로만 저장하고 SecureRandom 으로 생성한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PhoneVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsSender smsSender;

    @Value("${app.verification.code-length:6}") private int codeLength;
    @Value("${app.verification.ttl-seconds:300}") private long ttlSeconds;
    @Value("${app.verification.resend-cooldown-seconds:30}") private long resendCooldownSeconds;
    @Value("${app.verification.max-attempts:5}") private int maxAttempts;
    @Value("${app.verification.max-daily-requests:5}") private int maxDailyRequests;
    @Value("${app.verification.consume-window-seconds:600}") private long consumeWindowSeconds;

    @Transactional
    public void sendCode(String phone, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();

        verificationRepository.findTopByPhoneAndPurposeOrderByIdDesc(phone, purpose).ifPresent(latest -> {
            LocalDateTime canResendAt = latest.getCreatedAt().plusSeconds(resendCooldownSeconds);
            if (now.isBefore(canResendAt)) throw new BusinessException(ErrorCode.VERIFICATION_COOLDOWN);
        });

        long todayCount = verificationRepository.countByPhoneAndCreatedAtAfter(phone, now.minusDays(1));
        if (todayCount >= maxDailyRequests) throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT);

        String code = generateCode();
        verificationRepository.save(PhoneVerificationEntity.issue(
                phone, passwordEncoder.encode(code), purpose, now.plusSeconds(ttlSeconds)));

        smsSender.sendVerificationCode(phone, code);
        log.info("인증번호 발송 완료 phone={}****, purpose={}", phone.substring(0, Math.min(7, phone.length())), purpose);
    }

    @Transactional
    public void confirmCode(String phone, String code, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        PhoneVerificationEntity v = verificationRepository.findTopByPhoneAndPurposeOrderByIdDesc(phone, purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND));

        if (v.isUsed()) throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);
        if (v.isExpired(now)) throw new BusinessException(ErrorCode.VERIFICATION_EXPIRED);
        if (v.isAttemptExceeded(maxAttempts)) throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);

        v.increaseAttempt();
        if (!passwordEncoder.matches(code, v.getCodeHash())) throw new BusinessException(ErrorCode.VERIFICATION_CODE_MISMATCH);
        v.markVerified(now);
    }

    /** 가입 최종 제출 시 호출 - 클라이언트 값을 믿지 않고 DB로 재확인 후 즉시 소진 처리. */
    @Transactional
    public void consumeVerified(String phone, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        Optional<PhoneVerificationEntity> verified =
                verificationRepository.findVerified(phone, purpose, now.minusSeconds(consumeWindowSeconds));
        PhoneVerificationEntity v = verified.orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_REQUIRED));
        v.markUsed(now);
    }

    @Transactional
    @Scheduled(cron = "0 0 4 * * *")
    public void cleanUpExpired() {
        int deleted = verificationRepository.deleteOlderThan(LocalDateTime.now().minusDays(1));
        log.info("만료 인증 기록 {}건 정리", deleted);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        return String.format("%0" + codeLength + "d", RANDOM.nextInt(bound));
    }
}
