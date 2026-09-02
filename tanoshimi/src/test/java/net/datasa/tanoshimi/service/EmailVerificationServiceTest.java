package net.datasa.tanoshimi.service;

import net.datasa.tanoshimi.domain.entity.EmailVerificationEntity;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.EmailVerificationRepository;
import net.datasa.tanoshimi.util.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PhoneVerificationService와 동일한 구조/정책(쿨다운, 일일 한도, 만료, 시도횟수 제한)을
 * 이메일 채널로도 그대로 검증한다 - 알리고 SMS가 사업자등록번호 문제로 회원가입에는 못 쓰이게
 * 되면서 이 서비스가 실제로 signup 인증을 전담하게 됐다.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationRepository verificationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailSender emailSender;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(verificationRepository, passwordEncoder, emailSender);
        ReflectionTestUtils.setField(service, "codeLength", 6);
        ReflectionTestUtils.setField(service, "ttlSeconds", 300L);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 30L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "maxDailyRequests", 5);
        ReflectionTestUtils.setField(service, "consumeWindowSeconds", 600L);
    }

    // ---------------------------------------------------------------- sendCode

    @Test
    void sendCode_정상요청이면_저장하고_메일을_보낸다() {
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.empty());
        when(verificationRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");

        service.sendCode("user@test.com", VerificationPurpose.signup);

        ArgumentCaptor<EmailVerificationEntity> captor = ArgumentCaptor.forClass(EmailVerificationEntity.class);
        verify(verificationRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("user@test.com");
        verify(emailSender).sendVerificationCode(eq("user@test.com"), anyString());
    }

    @Test
    void sendCode_재요청_쿨다운_이내면_예외를_던지고_메일을_보내지_않는다() {
        EmailVerificationEntity recent = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(recent, "createdAt", LocalDateTime.now());
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc("user@test.com", VerificationPurpose.signup))
                .thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> service.sendCode("user@test.com", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_COOLDOWN);

        verify(emailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void sendCode_하루_요청_횟수를_초과하면_예외() {
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.empty());
        when(verificationRepository.countByEmailAndCreatedAtAfter(anyString(), any())).thenReturn(5L);

        assertThatThrownBy(() -> service.sendCode("user@test.com", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_DAILY_LIMIT);

        verify(emailSender, never()).sendVerificationCode(anyString(), anyString());
    }

    // ---------------------------------------------------------------- confirmCode

    @Test
    void confirmCode_요청내역이_없으면_예외() {
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmCode("user@test.com", "123456", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_NOT_FOUND);
    }

    @Test
    void confirmCode_이미_사용된_기록이면_예외() {
        EmailVerificationEntity v = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().plusMinutes(5));
        v.markUsed(LocalDateTime.now());
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.confirmCode("user@test.com", "123456", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_NOT_FOUND);
    }

    @Test
    void confirmCode_만료됐으면_예외() {
        EmailVerificationEntity v = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().minusSeconds(1));
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.confirmCode("user@test.com", "123456", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_EXPIRED);
    }

    @Test
    void confirmCode_시도횟수를_초과하면_예외() {
        EmailVerificationEntity v = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().plusMinutes(5));
        for (int i = 0; i < 5; i++) v.increaseAttempt();
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.of(v));

        assertThatThrownBy(() -> service.confirmCode("user@test.com", "123456", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_ATTEMPT_EXCEEDED);
    }

    @Test
    void confirmCode_코드가_틀리면_예외를_던지고_시도횟수가_증가한다() {
        EmailVerificationEntity v = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().plusMinutes(5));
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.of(v));
        when(passwordEncoder.matches("000000", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.confirmCode("user@test.com", "000000", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_CODE_MISMATCH);

        assertThat(v.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void confirmCode_코드가_맞으면_verified로_표시된다() {
        EmailVerificationEntity v = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().plusMinutes(5));
        when(verificationRepository.findTopByEmailAndPurposeOrderByIdDesc(anyString(), any())).thenReturn(Optional.of(v));
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        service.confirmCode("user@test.com", "123456", VerificationPurpose.signup);

        assertThat(v.isVerified()).isTrue();
    }

    // ---------------------------------------------------------------- consumeVerified

    @Test
    void consumeVerified_인증된_기록이_없으면_VERIFICATION_REQUIRED() {
        when(verificationRepository.findVerified(anyString(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consumeVerified("user@test.com", VerificationPurpose.signup))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.VERIFICATION_REQUIRED);
    }

    @Test
    void consumeVerified_인증된_기록이_있으면_소진처리한다() {
        EmailVerificationEntity v = EmailVerificationEntity.issue(
                "user@test.com", "hash", VerificationPurpose.signup, LocalDateTime.now().plusMinutes(5));
        v.markVerified(LocalDateTime.now());
        when(verificationRepository.findVerified(anyString(), any(), any())).thenReturn(Optional.of(v));

        service.consumeVerified("user@test.com", VerificationPurpose.signup);

        assertThat(v.isUsed()).isTrue();
    }

    // ---------------------------------------------------------------- cleanUpExpired

    @Test
    void cleanUpExpired_오래된_기록을_삭제한다() {
        when(verificationRepository.deleteOlderThan(any())).thenReturn(3);

        service.cleanUpExpired();

        verify(verificationRepository).deleteOlderThan(any());
    }
}
