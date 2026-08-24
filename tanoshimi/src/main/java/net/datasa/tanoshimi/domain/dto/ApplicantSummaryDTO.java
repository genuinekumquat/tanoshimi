package net.datasa.tanoshimi.domain.dto;

/**
 * 방장이 보는 신청자 목록 카드.
 * 팀 방침: "가입 심사 화면엔 닉네임·매너온도·국적만 공개" — 성별·나이는 절대 포함하지 않는다.
 * (실제 필드는 있지만 이 DTO 자체가 제외하도록 설계되어, 실수로 노출될 여지를 없앤다)
 */
public record ApplicantSummaryDTO(
        Long applicationId,
        Long applicantId,
        String nickname,
        double mannerTemp,
        String nationality,   // KR / JP 만 노출, 성별·생년월일은 미포함
        String message,
        String appliedAt
) {
}
