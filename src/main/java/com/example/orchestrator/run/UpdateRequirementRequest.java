package com.example.orchestrator.run;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRequirementRequest(@NotBlank @Size(max = 4000) String requirement) {
}