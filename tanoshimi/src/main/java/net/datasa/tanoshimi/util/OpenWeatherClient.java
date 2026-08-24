package net.datasa.tanoshimi.util;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.datasa.tanoshimi.domain.dto.WeatherResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.weather.provider", havingValue = "openweather")
@RequiredArgsConstructor
public class OpenWeatherClient implements WeatherClient {

    private final WebClient webClient = WebClient.builder().baseUrl("https://api.openweathermap.org/data/2.5").build();

    @Value("${app.weather.api-key}")
    private String apiKey;

    @Override
    public WeatherResult getForecast(double latitude, double longitude, LocalDate date) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenWeatherMap API key is missing. Falling back to default good weather.");
            return new WeatherResult("맑음", 25.0, 15.0, 0, true);
        }

        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/forecast")
                            .queryParam("lat", latitude)
                            .queryParam("lon", longitude)
                            .queryParam("appid", apiKey)
                            .queryParam("units", "metric")
                            .queryParam("lang", "kr")
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("list")) {
                String targetDateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                
                // Find a forecast for 12:00:00 of the target date, or the first available one for that day
                JsonNode targetForecast = null;
                for (JsonNode item : response.get("list")) {
                    String dtTxt = item.get("dt_txt").asText();
                    if (dtTxt.startsWith(targetDateStr)) {
                        targetForecast = item;
                        if (dtTxt.endsWith("12:00:00")) {
                            break;
                        }
                    }
                }

                if (targetForecast != null) {
                    JsonNode main = targetForecast.get("main");
                    JsonNode weather = targetForecast.get("weather").get(0);
                    
                    String condition = weather.get("description").asText();
                    double high = main.get("temp_max").asDouble();
                    double low = main.get("temp_min").asDouble();
                    
                    int precip = 0;
                    if (targetForecast.has("pop")) {
                        precip = (int)(targetForecast.get("pop").asDouble() * 100);
                    }
                    
                    boolean isGood = precip < 40; // simple threshold
                    
                    return new WeatherResult(condition, high, low, precip, isGood);
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch weather from OpenWeatherMap", e);
        }
        
        // Fallback if date is too far in the future or error occurs
        return new WeatherResult("알 수 없음", 20.0, 10.0, 10, true);
    }
}
