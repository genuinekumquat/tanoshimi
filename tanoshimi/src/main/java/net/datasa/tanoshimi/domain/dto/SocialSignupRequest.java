package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.Period;
import net.datasa.tanoshimi.util.UsernamePolicy;

/** 소셜 가입 추가정보. 이메일/소셜ID 는 서버 세션값만 사용(클라이언트가 위조 불가). */
public record SocialSignupRequest(
        @NotBlank @Pattern(regexp = "^[가-힣a-zA-Zぁ-んァ-ヶ一-龠]{2,20}$") String name,
        /** [vanity-url 신규] SignupRequest 와 동일한 규칙(UsernamePolicy) - 소셜 가입도 별도
         * 가입 경로라서 여기도 똑같이 받아야 한다(공유 검증 로직만 재사용, 필드 자체는 중복 정의). */
        @NotBlank String username,
        String email,   // provider 가 이메일을 안 줬을 때만 사용자가 직접 입력(그 외엔 무시하고 세션값 사용)
        @NotBlank @Pattern(regexp = "^01[016789][0-9]{7,8}$") String phone,
        @NotNull String gender,
        @NotNull LocalDate birthDate,
        @NotNull String nationality,
        boolean termsAgreed
) {
    @AssertTrue(message = "아이디는 영문 소문자로 시작하는 3~20자의 소문자/숫자/밑줄만 쓸 수 있어요.")
    public boolean isUsernameFormatValid() { return UsernamePolicy.isValidFormat(UsernamePolicy.normalize(username)); }

    @AssertTrue(message = "이용약관에 동의해야 가입할 수 있습니다.")
    public boolean isTermsChecked() { return termsAgreed; }

    @AssertTrue(message = "만 14세 이상만 가입할 수 있습니다.")
    public boolean isOldEnough() {
        return birthDate != null && Period.between(birthDate, LocalDate.now()).getYears() >= 14;
    }

    public SocialSignupRequest normalized() {
        return new SocialSignupRequest(name == null ? null : name.trim(),
                UsernamePolicy.normalize(username),
                email == null ? null : email.trim().toLowerCase(),
                phone == null ? null : phone.replaceAll("[^0-9]", ""),
                gender, birthDate, nationality, termsAgreed);
    }
}
