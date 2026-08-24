package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.domain.dto.WeatherResult;
import java.time.LocalDate;

/**
 * 날씨 조회 인터페이스.
 * 지금은 MockWeatherClient 가 좌표+날짜를 시드로 결정론적 더미값을 생성한다
 * (같은 지역·같은 날짜면 항상 같은 결과가 나와야 새로고침할 때마다 날씨가 바뀌는
 *  이상한 데모가 되지 않는다).
 */
public interface WeatherClient {
    WeatherResult getForecast(double latitude, double longitude, LocalDate date);
}
