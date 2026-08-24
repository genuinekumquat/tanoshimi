package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotBlank;

public record TranslateRequest(@NotBlank String text, @NotBlank String targetLang) {
}
