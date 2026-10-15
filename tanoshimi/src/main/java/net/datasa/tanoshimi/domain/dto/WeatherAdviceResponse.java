package net.datasa.tanoshimi.domain.dto;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class WeatherAdviceResponse {
	private boolean recommendable;
	private String weatherCondition;
	private String message;
	/** 같은 지역(또는 가까운 거리)의 실내 대체 장소. 날씨가 괜찮으면 null. */
	private List<WeatherAlternative> alternatives;
}