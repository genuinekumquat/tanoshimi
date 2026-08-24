package net.datasa.tanoshimi.domain.entity;

/** draft: 자유롭게 수정 가능(결제의무 없음) -> submitted: 액티비티 결제 대기 -> confirmed: 전원 결제 완료 */
public enum ScheduleStatus { draft, submitted, confirmed }
