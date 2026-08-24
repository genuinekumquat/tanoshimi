package net.datasa.tanoshimi.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartyApplyRequest(
        @NotBlank @Size(max = 300) String message
) {
}
