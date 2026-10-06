package com.example.orchestrator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import io.temporal.workflow.SignalMethod;

@WorkflowInterface
public interface OrchestrationWorkflow {
    @WorkflowMethod
    void execute(String runId);

    @SignalMethod
    void recordDecision(boolean approved);

    @SignalMethod
    void recordArtifactDecision(boolean accepted);

    @SignalMethod
    void recordClarification(String clarification);
}