package com.example.orchestrator.run;

import java.util.List;
import java.util.UUID;

public interface PlannedAgent {
    List<TaskBlueprint> plan(UUID runId, String requirement, String codebaseContext, RunScenario scenario);
}