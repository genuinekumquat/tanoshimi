package net.datasa.tanoshimi.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ApiResponse;
import net.datasa.tanoshimi.domain.entity.VerificationPurpose;
import net.datasa.tanoshimi.service.PhoneVerificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/verification/phone")
@RequiredArgsConstructor
public class PhoneVerificationApiController {

    private final PhoneVerificationService phoneVerificationService;

    public record SendReq(@NotBlank @Pattern(regexp = "^01[016789][0-9]{7,8}$") String phone) {
        String normalized() { return phone.replaceAll("[^0-9]", ""); }
    }
    public record ConfirmReq(@NotBlank String phone, @NotBlank @Pattern(regexp = "^[0-9]{6}$") String code) {
        String normalizedPhone() { return phone.replaceAll("[^0-9]", ""); }
    }

    @PostMapping("/send")
    public ApiResponse<Void> send(@Valid @RequestBody SendReq req) {
        phoneVerificationService.sendCode(req.normalized(), VerificationPurpose.signup);
        return ApiResponse.okMessage("인증번호를 발송했습니다. 5분 안에 입력해 주세요.");
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@Valid @RequestBody ConfirmReq req) {
        phoneVerificationService.confirmCode(req.normalizedPhone(), req.code(), VerificationPurpose.signup);
        return ApiResponse.okMessage("휴대폰 인증이 완료되었습니다.");
    }
}
