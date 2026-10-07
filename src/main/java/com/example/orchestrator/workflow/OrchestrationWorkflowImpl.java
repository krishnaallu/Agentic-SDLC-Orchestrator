package com.example.orchestrator.workflow;

import com.example.orchestrator.domain.TaskBlueprint;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OrchestrationWorkflowImpl implements OrchestrationWorkflow {
    private final OrchestrationActivities activities = Workflow.newActivityStub(OrchestrationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(3))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumAttempts(3)
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());
    private Boolean approved;
    private Boolean artifactsAccepted;
    private String clarification;

    @Override
    public void execute(String runId) {
        Workflow.await(() -> approved != null);
        if (!approved) {
            return;
        }

        activities.markRunStarted(runId);
        Set<String> completed = new HashSet<>();
        Set<String> scheduled = new HashSet<>();
        List<TaskBlueprint> plan = activities.loadPlan(runId);
        try {
            while (completed.size() < plan.size()) {
                List<TaskBlueprint> ready = plan.stream()
                        .filter(task -> !scheduled.contains(task.nodeKey()))
                        .filter(task -> completed.containsAll(task.dependencies()))
                        .toList();
                if (ready.isEmpty()) {
                    throw new IllegalStateException("Task graph cannot make progress; check dependencies for a cycle");
                }
                List<Promise<Void>> executions = new ArrayList<>();
                for (TaskBlueprint task : ready) {
                    scheduled.add(task.nodeKey());
                    executions.add(Async.procedure(() -> activities.executeTask(runId, task.nodeKey())));
                }
                Promise.allOf(executions).get();
                ready.forEach(task -> completed.add(task.nodeKey()));
                if (ready.stream().anyMatch(task -> task.nodeKey().equals("ambiguity-analysis"))) {
                    activities.markAwaitingClarification(runId);
                    Workflow.await(() -> clarification != null);
                    activities.recordClarification(runId, clarification);
                }
            }
            activities.markRunReadyForReview(runId);
            Workflow.await(() -> artifactsAccepted != null);
            activities.finalizeArtifactReview(runId, artifactsAccepted);
        } catch (RuntimeException exception) {
            activities.markRunFailed(runId, exception.getMessage() == null ? "Workflow task failed" : exception.getMessage());
            throw exception;
        }
    }

    @Override
    public void recordDecision(boolean decision) {
        if (approved == null) {
            approved = decision;
        }
    }

    @Override
    public void recordArtifactDecision(boolean accepted) {
        if (artifactsAccepted == null) {
            artifactsAccepted = accepted;
        }
    }

    @Override
    public void recordClarification(String clarificationAnswer) {
        if (clarification == null && clarificationAnswer != null && !clarificationAnswer.isBlank()) {
            clarification = clarificationAnswer;
        }
    }
}