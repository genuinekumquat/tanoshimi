package net.datasa.tanoshimi.domain.entity;

/** [v16 신규] 계획표 스냅샷이 자동저장(20분 주기)인지 수동저장인지 구분. */
public enum SnapshotTrigger { auto, manual, ai_valid }
