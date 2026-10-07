package com.example.orchestrator.application;

import com.example.orchestrator.domain.RunScenario;
import com.example.orchestrator.domain.TaskBlueprint;

import java.util.List;
import java.util.UUID;

public interface TaskExecutionAgent {
    List<ArtifactDraft> execute(UUID runId, String requirement, String codebaseContext, String clarificationNotes,
                                RunScenario scenario, TaskBlueprint task);
}