package com.example.orchestrator.run;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrchestrationRunResponse(
        UUID id,
        String requirement,
        RunScenario scenario,
        RunStatus status,
        String decisionActor,
        String decisionNote,
        Instant createdAt,
        Instant updatedAt,
        List<TaskResponse> tasks,
        List<AuditEventResponse> audit
) {
    public record TaskResponse(String nodeKey, String title, String description,
                               List<String> dependencies, TaskStatus status) {
    }

    public record AuditEventResponse(String action, String actor, String details, Instant happenedAt) {
    }
}