package com.example.orchestrator.api;

import com.example.orchestrator.domain.RunScenario;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRunRequest(
        @NotBlank @Size(max = 4000) String requirement,
        RunScenario scenario,
        @Size(max = 20000) String codebaseContext,
        @Size(max = 25) List<@Valid CodebaseSnapshotFile> codebaseSnapshot,
        @Size(max = 128) String snapshotRevision
) {
    public CreateRunRequest(String requirement, RunScenario scenario) {
        this(requirement, scenario, null, null, null);
    }

    public CreateRunRequest(String requirement, RunScenario scenario, String codebaseContext) {
        this(requirement, scenario, codebaseContext, null, null);
    }

    public CreateRunRequest(String requirement, RunScenario scenario, String codebaseContext,
                            List<CodebaseSnapshotFile> codebaseSnapshot) {
        this(requirement, scenario, codebaseContext, codebaseSnapshot, null);
    }

    public CreateRunRequest {
        if (scenario == null) {
            scenario = RunScenario.GREENFIELD;
        }
    }
}