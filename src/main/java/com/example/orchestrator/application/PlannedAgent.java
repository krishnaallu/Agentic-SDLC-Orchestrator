package com.example.orchestrator.application;

import com.example.orchestrator.domain.RunScenario;
import com.example.orchestrator.domain.TaskBlueprint;

import java.util.List;
import java.util.UUID;

public interface PlannedAgent {
    List<TaskBlueprint> plan(UUID runId, String requirement, String codebaseContext, RunScenario scenario);
}