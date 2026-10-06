package com.example.orchestrator.workflow;

import com.example.orchestrator.run.RunScenario;
import com.example.orchestrator.run.TaskBlueprint;

public record TaskExecutionContext(String runId, String requirement, String codebaseContext, String clarificationNotes, RunScenario scenario,
								   TaskBlueprint blueprint) {
}