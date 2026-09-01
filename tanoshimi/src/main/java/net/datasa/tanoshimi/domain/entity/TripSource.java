package net.datasa.tanoshimi.domain.entity;

/**
 * MyTripEntity.source - 이 여행 기록이 어떻게 생겼는지.
 * 담당: 김민규(⑥ 마이페이지). v19 신규(MyTripEntity 참고).
 */
public enum TripSource {
    /** 파티 완료 시 MyTripService.syncFromCompletedParties 가 자동으로 만든다. 직접 수정/삭제 불가. */
    PARTY,
    /** 사용자가 마이페이지 "내 여행"에서 직접 등록. 수정/삭제 가능. */
    SOLO
}
