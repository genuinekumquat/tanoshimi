package net.datasa.tanoshimi.domain.dto;
import lombok.Builder;
import lombok.Data;
import net.datasa.tanoshimi.domain.entity.ActivityEntity;
import java.util.List;

@Data
@Builder
public class WeatherAdviceResponse {
	private boolean isRecommendable;
	private String weatherCondition;
	private String message;
	private List<ActivityEntity> alternatives;
}