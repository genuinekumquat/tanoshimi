package net.datasa.tanoshimi.domain.dto;

/**
 * 날씨 조회 결과. DB에 저장하지 않고 그때그때 조회한다(팀 방침 - 매일 바뀌는 값을 저장하면 낡은 정보가 됨).
 */
public record WeatherResult(
        String condition,      // 맑음/흐림/비/눈/폭염/한파
        double tempHighC,
        double tempLowC,
        int precipProb,        // 강수확률 %
        boolean isGood         // 야외 액티비티 하기 좋은 날씨인지 종합 판단
) {
    public boolean isBad() { return !isGood; }
}
