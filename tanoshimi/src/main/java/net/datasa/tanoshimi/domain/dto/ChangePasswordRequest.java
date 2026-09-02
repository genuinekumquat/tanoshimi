package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import net.datasa.tanoshimi.util.PasswordPolicy;

/** 비밀번호 변경(자발적 변경 / 임시 비밀번호 발급 후 강제 변경 공용). */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword,
        @NotBlank String newPasswordConfirm
) {
    @AssertTrue(message = "새 비밀번호가 일치하지 않습니다.")
    public boolean isNewPasswordConfirmed() { return newPassword != null && newPassword.equals(newPasswordConfirm); }

    @AssertTrue(message = "비밀번호는 8~20자, 영문/숫자/특수문자 중 2가지 이상 조합이어야 합니다.")
    public boolean isNewPasswordValid() { return PasswordPolicy.isValid(newPassword); }
}
