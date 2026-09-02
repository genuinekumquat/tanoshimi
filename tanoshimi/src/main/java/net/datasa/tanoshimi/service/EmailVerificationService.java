package net.datasa.tanoshimi.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.entity.EmailVerificationEntity;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.EmailVerificationRepository;
import net.datasa.tanoshimi.util.EmailSender;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이메일 본인인증 발급/검증. 인증번호는 해시로만 저장하고 SecureRandom 으로 생성한다.
 *
 * PhoneVerificationService와 구조가 동일하다 - 알리고 등 SMS API가 사업자등록번호 없이는
 * 실사용이 어려워 회원가입 시 본인인증 채널을 이메일로 전환했다. app.verification.* 설정값도
 * 그대로 공유해서 쓴다(코드 길이/유효시간/재요청 쿨다운 등 정책은 채널과 무관하게 동일).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;

    @Value("${app.verification.code-length:6}") private int codeLength;
    @Value("${app.verification.ttl-seconds:300}") private long ttlSeconds;
    @Value("${app.verification.resend-cooldown-seconds:30}") private long resendCooldownSeconds;
    @Value("${app.verification.max-attempts:5}") private int maxAttempts;
    @Value("${app.verification.max-daily-requests:5}") private int maxDailyRequests;
    @Value("${app.verification.consume-window-seconds:600}") private long consumeWindowSeconds;

    @Transactional
    public void sendCode(String email, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();

        verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose).ifPresent(latest -> {
            LocalDateTime canResendAt = latest.getCreatedAt().plusSeconds(resendCooldownSeconds);
            if (now.isBefore(canResendAt)) throw new BusinessException(ErrorCode.VERIFICATION_COOLDOWN);
        });

        long todayCount = verificationRepository.countByEmailAndCreatedAtAfter(email, now.minusDays(1));
        if (todayCount >= maxDailyRequests) throw new BusinessException(ErrorCode.VERIFICATION_DAILY_LIMIT);

        String code = generateCode();
        verificationRepository.save(EmailVerificationEntity.issue(
                email, passwordEncoder.encode(code), purpose, now.plusSeconds(ttlSeconds)));

        emailSender.sendVerificationCode(email, code);
        log.info("이메일 인증번호 발송 완료 email={}, purpose={}", mask(email), purpose);
    }

    @Transactional
    public void confirmCode(String email, String code, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        EmailVerificationEntity v = verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(email, purpose)
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
    public void consumeVerified(String email, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        Optional<EmailVerificationEntity> verified =
                verificationRepository.findVerified(email, purpose, now.minusSeconds(consumeWindowSeconds));
        EmailVerificationEntity v = verified.orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_REQUIRED));
        v.markUsed(now);
    }

    @Transactional
    @Scheduled(cron = "0 5 4 * * *")
    public void cleanUpExpired() {
        int deleted = verificationRepository.deleteOlderThan(LocalDateTime.now().minusDays(1));
        log.info("만료된 이메일 인증 기록 {}건 정리", deleted);
    }

    private String generateCode() {
        int bound = (int) Math.pow(10, codeLength);
        return String.format("%0" + codeLength + "d", RANDOM.nextInt(bound));
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
