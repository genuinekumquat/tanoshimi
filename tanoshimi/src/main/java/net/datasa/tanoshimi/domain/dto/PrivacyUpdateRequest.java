package net.datasa.tanoshimi.domain.dto;

/** [account-settings 신규] 계정 공개범위 변경 요청. */
public record PrivacyUpdateRequest(boolean isPrivate) {}
