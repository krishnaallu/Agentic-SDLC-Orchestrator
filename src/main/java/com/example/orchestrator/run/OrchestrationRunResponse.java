package com.example.orchestrator.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrchestrationRunResponse(
        UUID id,
        String requirement,
        String originalRequirement,
        String codebaseContext,
        String clarificationNotes,
        int planVersion,
        RunScenario scenario,
        RunStatus status,
        String decisionActor,
        String decisionNote,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant finishedAt,
        long endToEndLatencyMillis,
        List<TaskResponse> tasks,
        List<AuditEventResponse> audit,
        List<ArtifactResponse> artifacts
) {
    public record TaskResponse(String nodeKey, String title, String description,
                               List<String> dependencies, TaskStatus status, int attemptCount,
                               String outputSummary, Instant completedAt) {
    }

    public record AuditEventResponse(String action, String actor, String details, Instant happenedAt) {
    }

    public record ArtifactResponse(String taskKey, String path, String mediaType, String content,
                                   ArtifactStatus status, Instant createdAt) {
    }
}