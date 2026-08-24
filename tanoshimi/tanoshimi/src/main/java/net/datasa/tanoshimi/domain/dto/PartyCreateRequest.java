package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.time.LocalDate;

/** 파티 만들기 폼 요청. */
public record PartyCreateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String region,
        @NotNull LocalDate departureDate,
        Integer budgetKrw,
        @Min(1) @Max(20) int capacity,
        String styleTag,
        @NotBlank String genderRestriction,        // all / male_only / female_only
        Integer ageMin,
        Integer ageMax,
        @NotBlank String nationalityRestriction,    // all / kr_only / jp_only
        String thumbnailUrl,
        Long tourId
) {
}
