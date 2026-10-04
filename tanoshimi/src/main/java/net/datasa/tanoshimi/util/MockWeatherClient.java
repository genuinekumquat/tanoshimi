package net.datasa.tanoshimi.util;

import net.datasa.tanoshimi.domain.dto.WeatherResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Random;

/**
 * 개발용 더미 날씨 클라이언트.
 * 좌표+날짜를 해시 시드로 써서 "같은 지역·같은 날짜 = 항상 같은 결과" 를 보장한다.
 * 실제 서비스에서는 WeatherClient 구현체를 실제 API 연동으로 교체하면 된다
 * (인터페이스만 맞으면 이 클래스를 지우기만 하면 됨).
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.weather.provider", havingValue = "mock", matchIfMissing = true)
public class MockWeatherClient implements WeatherClient {
    
    // [TNSM-72] 예전엔 계절 상관없이 6종류 중 완전 랜덤으로 뽑아서, 10월에 "눈"이 나오는 등
    // 비현실적인 결과가 나왔다. 월(month) 기준으로 그 계절에 실제로 있을 법한 날씨만 후보로 좁힌다.
    // [TNSM-72] 주의: 후보 배열 길이를 2의 거듭제곱(4, 8...)으로 두면 안 된다. java.util.Random의
    // nextInt(bound)는 bound가 2의 거듭제곱일 때 상위 비트만 뽑는 다른 경로를 타는데, 이 경로가
    // 날짜(epoch day)처럼 1씩 증가하는 시드에서는 여러 날 연속으로 같은 값만 나오는 패턴을 만들어낸다
    // (실제로 4개짜리 배열로 테스트해보니 한달 내내 "비"만 나오는 버그가 있었음). 그래서 전부 5개로 맞춘다.
    private static final String[] WINTER = {"맑음", "흐림", "눈", "한파", "한파"};
    private static final String[] SPRING = {"맑음", "맑음", "흐림", "흐림", "비"};
    private static final String[] SUMMER = {"맑음", "흐림", "비", "비", "폭염"};
    private static final String[] AUTUMN = {"맑음", "맑음", "흐림", "흐림", "비"};
    
    private static String[] conditionsFor(LocalDate date) {
        return switch (date.getMonthValue()) {
            case 12, 1, 2 -> WINTER;
            case 3, 4, 5 -> SPRING;
            case 6, 7, 8 -> SUMMER;
            default -> AUTUMN; // 9, 10, 11
        };
    }
    
    @Override
    public WeatherResult getForecast(double latitude, double longitude, LocalDate date) {
        long seed = Math.round(latitude * 1000) + Math.round(longitude * 1000) + date.toEpochDay();
        Random random = new Random(seed);
        
        String[] candidates = conditionsFor(date);
        String condition = candidates[random.nextInt(candidates.length)];
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