package net.datasa.tanoshimi.domain.dto;

/** [account-settings 신규] 계정 관리 - 민감한 동작 전 "비밀번호 확인" 단계 요청. */
public record PasswordVerifyRequest(String password) {}
