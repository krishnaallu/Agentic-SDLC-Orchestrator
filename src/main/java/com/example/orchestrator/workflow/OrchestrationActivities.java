package com.example.orchestrator.workflow;

import com.example.orchestrator.run.TaskBlueprint;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.List;

@ActivityInterface
public interface OrchestrationActivities {
    @ActivityMethod
    List<TaskBlueprint> loadPlan(String runId);

    @ActivityMethod
    void markRunStarted(String runId);

    @ActivityMethod
    void markAwaitingClarification(String runId);

    @ActivityMethod
    void recordClarification(String runId, String clarification);

    @ActivityMethod
    void executeTask(String runId, String taskKey);

    @ActivityMethod
    void markRunReadyForReview(String runId);

    @ActivityMethod
    void finalizeArtifactReview(String runId, boolean accepted);

    @ActivityMethod
    void markRunFailed(String runId, String reason);
}