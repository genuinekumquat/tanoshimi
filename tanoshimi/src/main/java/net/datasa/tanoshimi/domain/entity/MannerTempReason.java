package net.datasa.tanoshimi.domain.entity;

/**
 * [v16 신규] 매너온도 가산/감산 사유 - MannerTempService 가 단독으로 사용한다.
 * party_complete: 파티 완료 시 전원 +0.5 / host_bonus: 그중 방장 추가 +0.3
 * report_penalty: 신고 3회 누적 도달 시 -1.0(반복 적용) / leave_penalty: 중도이탈·강퇴 -0.5
 */
public enum MannerTempReason { party_complete, host_bonus, report_penalty, leave_penalty }
