package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.service.EmailVerificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verification/email")
@RequiredArgsConstructor
public class EmailVerificationApiController {

    private final EmailVerificationService emailVerificationService;

    public record SendReq(@NotBlank @Email String email) {
        String normalized() { return email.trim().toLowerCase(); }
    }
    public record ConfirmReq(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
        String normalizedEmail() { return email.trim().toLowerCase(); }
    }

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody SendReq req) {
        emailVerificationService.sendCode(req.normalized(), VerificationPurpose.signup);
        return ApiResponse.okMessage("인증번호를 발송했습니다. 5분 안에 입력해 주세요.");
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody ConfirmReq req) {
        emailVerificationService.confirmCode(req.normalizedEmail(), req.code(), VerificationPurpose.signup);
        return ApiResponse.okMessage("이메일 인증이 완료되었습니다.");
    }
}
