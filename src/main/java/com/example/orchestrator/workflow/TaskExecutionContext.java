package com.example.orchestrator.workflow;

import com.example.orchestrator.domain.RunScenario;
import com.example.orchestrator.domain.TaskBlueprint;

public record TaskExecutionContext(String runId, String requirement, String codebaseContext, String clarificationNotes, RunScenario scenario,
								   TaskBlueprint blueprint) {
}