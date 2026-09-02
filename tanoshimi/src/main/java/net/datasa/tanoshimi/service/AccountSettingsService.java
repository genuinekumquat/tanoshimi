package net.datasa.tanoshimi.service;

import lombok.RequiredArgsConstructor;
import net.datasa.tanoshimi.domain.dto.ProfileUpdateRequest;
import net.datasa.tanoshimi.domain.entity.Gender;
import net.datasa.tanoshimi.domain.entity.Nationality;
import net.datasa.tanoshimi.domain.entity.UserEntity;
import net.datasa.tanoshimi.exception.BusinessException;
import net.datasa.tanoshimi.exception.ErrorCode;
import net.datasa.tanoshimi.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * [account-settings 신규] 마이페이지 &gt; 계정 관리(/mypage/account) 전용 서비스.
 * UserService 는 가입/소셜가입 흐름을 담당하므로, 계정 관리 화면의 "회원정보 수정" +
 * "공개범위 변경" 로직은 여기서 따로 묶는다(비밀번호 재확인은 UserService.verifyPassword 를
 * 그대로 재사용).
 */
@Service
@RequiredArgsConstructor
public class AccountSettingsService {

    /** SignupRequest 의 전화번호 정규식과 동일 - 새 검증 규칙을 만들지 않고 그대로 재사용한다. */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[016789][0-9]{7,8}$");

    private final UserRepository userRepository;
    private final UserService userService;

    /**
     * 회원정보(이름/전화/성별/생년월일/국적) 수정.
     *
     * <p><b>비밀번호 재확인 정책</b>: 일반(로컬) 계정은 반드시 현재 비밀번호가 맞아야 수정을
     * 허용한다. 소셜 전용 계정({@link UserEntity#isSocialAccount()}==true)은 password 필드에
     * 알 수 없는 랜덤 해시가 들어있어(가입 시 unusablePasswordHash) 사용자가 그 값을 알 방법이
     * 없으므로, 재확인 없이 바로 수정을 허용한다 - "비밀번호를 입력하라"고 요구하면 소셜
     * 사용자는 영원히 이 기능을 못 쓰게 되는 쪽이 더 나쁜 경험이라고 판단했다(단, 화면에서는
     * 소셜 계정에는 비밀번호 입력란 자체를 안 보여줘서 혼란을 줄인다).
     */
    @Transactional
    public void updateProfile(UserEntity user, ProfileUpdateRequest req) {
        if (!user.isSocialAccount()) {
            if (!userService.verifyPassword(user, req.password())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 일치하지 않습니다.");
            }
        }

        String name = req.name() == null ? null : req.name().trim();
        String phone = req.phone() == null ? null : req.phone().replaceAll("[^0-9]", "");

        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이름을 입력해 주세요.");
        }
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "휴대폰 번호 형식이 올바르지 않습니다.");
        }
        if (!phone.equals(user.getPhone()) && userRepository.existsByPhone(phone)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE);
        }
        if (req.gender() == null || req.birthDate() == null || req.nationality() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Gender gender = Gender.valueOf(req.gender());
        Nationality nationality = Nationality.valueOf(req.nationality());

        user.changePersonalInfo(name, phone, gender, req.birthDate(), nationality);
    }

    @Transactional
    public void changeVisibility(UserEntity user, boolean isPrivate) {
        user.changeVisibility(isPrivate);
    }
}
