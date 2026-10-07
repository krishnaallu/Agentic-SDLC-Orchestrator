package com.example.orchestrator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ArtifactDecisionRequest(
        @NotNull Boolean approved,
        @NotBlank @Size(max = 200) String actor,
        @Size(max = 2000) String note
) {
}