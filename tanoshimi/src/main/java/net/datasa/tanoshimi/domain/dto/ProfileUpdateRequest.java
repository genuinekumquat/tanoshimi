package net.datasa.tanoshimi.domain.dto;

import java.time.LocalDate;

/**
 * [account-settings 신규] 계정 관리 > 회원정보조회 탭의 "수정" 요청.
 * 이메일은 로그인 ID 라 여기 포함하지 않는다(불변 - SignupRequest 에도 이메일 변경 경로가 없다).
 * password 는 새 비밀번호가 아니라 "현재 비밀번호 재확인"용이다 - 소셜 계정은 서버에서
 * AccountSettingsService 가 이 필드를 무시한다(진짜 비밀번호를 모르므로 - UserService.verifyPassword 참고).
 */
public record ProfileUpdateRequest(
        String password,
        String name,
        String phone,
        String gender,
        LocalDate birthDate,
        String nationality
) {}
