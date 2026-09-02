package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.Period;
import net.datasa.tanoshimi.util.PasswordPolicy;
import net.datasa.tanoshimi.util.UsernamePolicy;

/** 이메일 회원가입. role/status 는 받지 않는다(권한 상승 방지). */
public record SignupRequest(
        @NotBlank @Email String email,
        /** [vanity-url 신규] 프로필 URL(/{username}) 아이디. 형식/예약어 검증은 UsernamePolicy 참고 -
         * 클라이언트가 이미 걸러 보내더라도 서버가 다시 확인한다(신뢰하지 않음). */
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String passwordConfirm,
        @NotBlank @Pattern(regexp = "^[가-힣a-zA-Zぁ-んァ-ヶ一-龠]{2,20}$", message = "이름 형식이 올바르지 않습니다.") String name,
        @NotBlank @Pattern(regexp = "^01[016789][0-9]{7,8}$") String phone,
        @NotNull String gender,          // "male" / "female"
        @NotNull LocalDate birthDate,
        @NotNull String nationality,     // "KR" / "JP"
        boolean termsAgreed
) {
    @AssertTrue(message = "아이디는 영문 소문자로 시작하는 3~20자의 소문자/숫자/밑줄만 쓸 수 있어요.")
    public boolean isUsernameFormatValid() { return UsernamePolicy.isValidFormat(UsernamePolicy.normalize(username)); }

    @AssertTrue(message = "비밀번호가 일치하지 않습니다.")
    public boolean isPasswordConfirmed() { return password != null && password.equals(passwordConfirm); }

    @AssertTrue(message = "비밀번호는 8~20자, 영문/숫자/특수문자 중 2가지 이상 조합이어야 합니다.")
    public boolean isPasswordValid() { return PasswordPolicy.isValid(password); }

    @AssertTrue(message = "이용약관에 동의해야 가입할 수 있습니다.")
    public boolean isTermsChecked() { return termsAgreed; }

    @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
    public boolean isOldEnough() {
        return birthDate != null && Period.between(birthDate, LocalDate.now()).getYears() >= 14;
    }

    public SignupRequest normalized() {
        return new SignupRequest(
                email == null ? null : email.trim().toLowerCase(),
                UsernamePolicy.normalize(username), password, passwordConfirm,
                name == null ? null : name.trim(),
                phone == null ? null : phone.replaceAll("[^0-9]", ""),
                gender, birthDate, nationality, termsAgreed);
    }
}
