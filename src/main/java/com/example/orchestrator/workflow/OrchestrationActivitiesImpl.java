package com.example.orchestrator.workflow;

import com.example.orchestrator.domain.TaskBlueprint;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OrchestrationActivitiesImpl implements OrchestrationActivities {
    private final OrchestrationActivityService activityService;

    public OrchestrationActivitiesImpl(OrchestrationActivityService activityService) {
        this.activityService = activityService;
    }

    @Override
    public List<TaskBlueprint> loadPlan(String runId) {
        return activityService.loadPlan(runId);
    }

    @Override
    public void markRunStarted(String runId) {
        activityService.markRunStarted(runId);
    }

    @Override
    public void markAwaitingClarification(String runId) {
        activityService.markAwaitingClarification(runId);
    }

    @Override
    public void recordClarification(String runId, String clarification) {
        activityService.recordClarification(runId, clarification);
    }

    @Override
    public void executeTask(String runId, String taskKey) {
        activityService.executeTask(runId, taskKey);
    }

    @Override
    public void markRunReadyForReview(String runId) {
        activityService.markRunReadyForReview(runId);
    }

    @Override
    public void finalizeArtifactReview(String runId, boolean accepted) {
        activityService.finalizeArtifactReview(runId, accepted);
    }

    @Override
    public void markRunFailed(String runId, String reason) {
        activityService.markRunFailed(runId, reason);
    }
}