package net.datasa.tanoshimi.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * SMTP로 실제 인증 메일 발송. {@code app.email.provider=smtp} 일 때만 활성화된다
 * (기본값은 LogEmailSender - 개발 중 콘솔 출력).
 *
 * 알리고 SMS(Day 3 원안)는 사업자등록번호 없이는 실사용이 불가능해서 이메일 인증으로
 * 전환했다 - 이메일은 회사 등록 없이 일반 SMTP(Gmail 앱 비밀번호 등)로도 바로 쓸 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;

    @Value("${app.email.smtp.from}")
    private String from;

    @Override
    public void sendVerificationCode(String email, String code) {
        send(email, "[타노시미] 이메일 인증번호", "인증번호는 " + code + " 입니다. 5분 이내에 입력해 주세요.");
    }

    @Override
    public void sendTemporaryPassword(String email, String tempPassword) {
        send(email, "[타노시미] 임시 비밀번호 발급", "임시 비밀번호는 " + tempPassword + " 입니다. 로그인 후 반드시 비밀번호를 변경해 주세요.");
    }

    private void send(String email, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        message.setText(text);

        try {
            mailSender.send(message);
            log.info("이메일 발송 성공: to={}", mask(email));
        } catch (MailException e) {
            log.error("이메일 발송 실패: to={}", mask(email), e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}
