package com.example.orchestrator.run;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateRunRequest(
        @NotBlank @Size(max = 4000) String requirement,
        RunScenario scenario
) {
    public CreateRunRequest {
        if (scenario == null) {
            scenario = RunScenario.GREENFIELD;
        }
    }
}