package net.datasa.tanoshimi.domain.dto;

import java.util.List;
import net.datasa.tanoshimi.domain.entity.TourEntity;

/** AI 챗봇이 예약 전 보여주는 날씨 조언. bad 인 경우에만 alternatives 가 채워진다. */
public record WeatherAdviceDTO(
        WeatherResult weather,
        String message,          // "1월 1일 오사카는 강수확률 70%로 비 소식이 있어요..." 같은 챗봇 문구
        boolean recommend,        // true = 그대로 추천, false = 비추천(그래도 진행은 가능)
        List<TourEntity> alternatives  // 같은 날짜, 날씨 좋은 다른 지역 패키지 (최대 3개)
) {
}
