package net.datasa.tanoshimi.util;

import java.time.LocalDate;
import java.util.Random;
import net.datasa.tanoshimi.domain.dto.WeatherResult;
import org.springframework.stereotype.Component;

/**
 * 개발용 더미 날씨 클라이언트.
 * 좌표+날짜를 해시 시드로 써서 "같은 지역·같은 날짜 = 항상 같은 결과" 를 보장한다.
 * 실제 서비스에서는 WeatherClient 구현체를 실제 API 연동으로 교체하면 된다
 * (인터페이스만 맞으면 이 클래스를 지우기만 하면 됨).
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.weather.provider", havingValue = "mock", matchIfMissing = true)
public class MockWeatherClient implements WeatherClient {

    private static final String[] CONDITIONS = {"맑음", "흐림", "비", "눈", "폭염", "한파"};

    @Override
    public WeatherResult getForecast(double latitude, double longitude, LocalDate date) {
        long seed = Math.round(latitude * 1000) + Math.round(longitude * 1000) + date.toEpochDay();
        Random random = new Random(seed);

        String condition = CONDITIONS[random.nextInt(CONDITIONS.length)];
        double base = 15 + random.nextDouble() * 15;
        double high = Math.round((base + 3) * 10) / 10.0;
        double low = Math.round((base - 5) * 10) / 10.0;
        int precip = switch (condition) {
            case "비" -> 60 + random.nextInt(35);
            case "눈" -> 50 + random.nextInt(30);
            default -> random.nextInt(30);
        };

        boolean isGood = switch (condition) {
            case "비", "눈", "폭염", "한파" -> false;
            default -> precip < 40;
        };

        return new WeatherResult(condition, high, low, precip, isGood);
    }
}
