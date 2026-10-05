package net.datasa.tanoshimi.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationDto {
    private String kind; // "recommend" (existing activity) or "custom"
    private Long activityId; // null if custom
    private String title;
    private int durationMin;
    private int priceKrw; // optional
    private String description;
    private Double latitude;
    private Double longitude;
    private String venueType; // "indoor" | "outdoor" | "mixed", custom일 때만 AI가 채움
}

