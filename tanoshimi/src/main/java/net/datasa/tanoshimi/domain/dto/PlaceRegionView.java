package net.datasa.tanoshimi.domain.dto;

import net.datasa.tanoshimi.domain.entity.ActivityEntity;

import java.util.List;

/**
 * 관광지 둘러보기(/recommendations) 화면의 지역 한 덩어리 - 그 지역 관광지 목록과,
 * "이 지역 파티 보기" 버튼에 붙일 지금 모집 중인 파티 수.
 */
public record PlaceRegionView(String region, List<ActivityEntity> places, int openPartyCount) {
}
