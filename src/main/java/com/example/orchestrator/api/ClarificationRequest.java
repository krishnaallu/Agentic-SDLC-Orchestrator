package com.example.orchestrator.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClarificationRequest(@NotBlank @Size(max = 4000) String answers,
                                   @NotBlank @Size(max = 200) String actor) {
}