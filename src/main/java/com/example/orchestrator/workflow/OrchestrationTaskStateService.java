package com.example.orchestrator.workflow;

import com.example.orchestrator.application.ArtifactDraft;
import com.example.orchestrator.domain.ArtifactStatus;
import com.example.orchestrator.domain.RunStatus;
import com.example.orchestrator.domain.TaskBlueprint;
import com.example.orchestrator.domain.TaskStatus;
import com.example.orchestrator.persistence.AuditEvent;
import com.example.orchestrator.persistence.AuditEventRepository;
import com.example.orchestrator.persistence.OrchestrationArtifact;
import com.example.orchestrator.persistence.OrchestrationArtifactRepository;
import com.example.orchestrator.persistence.OrchestrationRun;
import com.example.orchestrator.persistence.OrchestrationRunRepository;
import com.example.orchestrator.persistence.OrchestrationTask;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrchestrationTaskStateService {
    private final OrchestrationRunRepository runRepository;
    private final AuditEventRepository auditRepository;
    private final OrchestrationArtifactRepository artifactRepository;

    public OrchestrationTaskStateService(OrchestrationRunRepository runRepository,
                                         AuditEventRepository auditRepository,
                                         OrchestrationArtifactRepository artifactRepository) {
        this.runRepository = runRepository;
        this.auditRepository = auditRepository;
        this.artifactRepository = artifactRepository;
    }

    @Transactional
    public TaskExecutionContext beginTask(String runId, String taskKey) {
        OrchestrationRun run = findRun(runId);
        OrchestrationTask task = findTask(run, taskKey);
        if (task.getStatus() == TaskStatus.SUCCEEDED) {
            return null;
        }
        task.markRunning();
        auditRepository.save(event(run, "TASK_STARTED", taskKey + " attempt " + task.getAttemptCount()));
        return new TaskExecutionContext(run.getId().toString(), run.getRequirement(), run.getCodebaseContext(), run.getClarificationNotes(),
            run.getScenario(), new TaskBlueprint(task.getNodeKey(), task.getTitle(), task.getDescription(), task.getDependencies()));
    }

    @Transactional
    public void completeTask(String runId, String taskKey, List<ArtifactDraft> drafts) {
        OrchestrationRun run = findRun(runId);
        OrchestrationTask task = findTask(run, taskKey);
        for (ArtifactDraft draft : drafts) {
            if (!artifactRepository.existsByRun_IdAndPath(run.getId(), draft.path())) {
                artifactRepository.save(new OrchestrationArtifact(UUID.randomUUID(), run, taskKey,
                        draft.path(), draft.mediaType(), draft.content(), ArtifactStatus.PROPOSED));
            }
        }
        task.markSucceeded("Generated " + drafts.size() + " reviewable artifact(s)");
        auditRepository.save(event(run, "TASK_SUCCEEDED", taskKey + ": " + drafts.size() + " artifact(s)"));
    }

    @Transactional
    public void failTask(String runId, String taskKey, String reason) {
        OrchestrationRun run = findRun(runId);
        OrchestrationTask task = findTask(run, taskKey);
        task.markFailed(safeSummary(reason));
        auditRepository.save(event(run, "TASK_ATTEMPT_FAILED", taskKey + ": " + safeSummary(reason)));
    }

    @Transactional
    public void recordValidationEvidence(String runId, ArtifactDraft evidence) {
        OrchestrationRun run = findRun(runId);
        OrchestrationArtifact existing = artifactRepository.findByRun_IdOrderByPathAsc(run.getId()).stream()
                .filter(artifact -> artifact.getPath().equals(evidence.path()))
                .findFirst().orElse(null);
        if (existing == null) {
            artifactRepository.save(new OrchestrationArtifact(UUID.randomUUID(), run, "final-validation",
                    evidence.path(), evidence.mediaType(), evidence.content(), ArtifactStatus.PROPOSED));
        } else {
            existing.setContent(evidence.content());
        }
        auditRepository.save(event(run, "VALIDATION_EVIDENCE_RECORDED",
                "Isolated validation report captured with exit evidence"));
    }

    @Transactional
    public void recordAgentFallback(String runId, String stage, String providerFailureType) {
        OrchestrationRun run = findRun(runId);
        auditRepository.save(event(run, "AGENT_FALLBACK", stage + " used deterministic template after provider failure: "
                + providerFailureType));
    }

    private OrchestrationRun findRun(String runId) {
        return runRepository.findById(UUID.fromString(runId))
                .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
    }

    private OrchestrationTask findTask(OrchestrationRun run, String taskKey) {
        return run.getTasks().stream().filter(task -> task.getNodeKey().equals(taskKey)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Task not found: " + taskKey));
    }

    private AuditEvent event(OrchestrationRun run, String action, String details) {
        return new AuditEvent(UUID.randomUUID(), run, action, "temporal-worker", details, Instant.now());
    }

    private String safeSummary(String value) {
        if (value == null || value.isBlank()) {
            return "Unspecified execution failure";
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }
}