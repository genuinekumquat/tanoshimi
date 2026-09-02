package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * JavaMailSender는 Spring이 제공하는 인터페이스라 실제 SMTP 서버 없이도 mock으로
 * 손쉽게 검증할 수 있다(AligoSmsSender 때처럼 WebClient용 가짜 서버를 띄울 필요가 없음).
 */
@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock
    private JavaMailSender mailSender;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender(mailSender);
        ReflectionTestUtils.setField(sender, "from", "no-reply@tanoshimi.local");
    }

    @Test
    void 정상_발송이면_수신자와_인증번호가_포함된_메일을_보낸다() {
        sender.sendVerificationCode("user@test.com", "123456");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@test.com");
        assertThat(sent.getFrom()).isEqualTo("no-reply@tanoshimi.local");
        assertThat(sent.getText()).contains("123456");
    }

    @Test
    void 발송_중_MailException이_나면_EMAIL_SEND_FAILED로_변환된다() {
        doThrow(new MailSendException("smtp connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.sendVerificationCode("user@test.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    void 임시비밀번호_발송이면_수신자와_임시비밀번호가_포함된_메일을_보낸다() {
        sender.sendTemporaryPassword("user@test.com", "Ab3xQ92kFz");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getTo()).containsExactly("user@test.com");
        assertThat(sent.getText()).contains("Ab3xQ92kFz");
    }

    @Test
    void 임시비밀번호_발송중_MailException이_나면_EMAIL_SEND_FAILED로_변환된다() {
        doThrow(new MailSendException("smtp connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender.sendTemporaryPassword("user@test.com", "Ab3xQ92kFz"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }
}
