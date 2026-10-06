package com.example.orchestrator.run;

import java.util.List;
import java.util.UUID;

public interface TaskExecutionAgent {
    List<ArtifactDraft> execute(UUID runId, String requirement, String codebaseContext, String clarificationNotes,
                                RunScenario scenario, TaskBlueprint task);
}