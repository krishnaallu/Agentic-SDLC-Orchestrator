package com.example.orchestrator.workflow;

import com.example.orchestrator.application.ArtifactDraft;
import com.example.orchestrator.application.GeneratedProjectIdentity;
import com.example.orchestrator.application.TaskExecutionAgent;
import com.example.orchestrator.domain.ArtifactStatus;
import com.example.orchestrator.domain.RunStatus;
import com.example.orchestrator.domain.TaskBlueprint;
import com.example.orchestrator.persistence.AuditEvent;
import com.example.orchestrator.persistence.AuditEventRepository;
import com.example.orchestrator.persistence.OrchestrationArtifact;
import com.example.orchestrator.persistence.OrchestrationArtifactRepository;
import com.example.orchestrator.persistence.OrchestrationRun;
import com.example.orchestrator.persistence.OrchestrationRunRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OrchestrationActivityService {
    private static final int MAX_ARTIFACTS_PER_TASK = 20;
    private static final int MAX_ARTIFACT_CHARACTERS = 100_000;
        private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
                "text/x-java-source", "text/markdown", "text/plain", "application/xml", "application/json", "text/yaml",
                "text/x-dockerfile");

    private final OrchestrationRunRepository runRepository;
    private final AuditEventRepository auditRepository;
    private final OrchestrationArtifactRepository artifactRepository;
    private final TaskExecutionAgent taskExecutionAgent;
    private final OrchestrationTaskStateService taskStateService;
    private final ProposalValidator proposalValidator;
    private final IsolatedProjectValidator isolatedProjectValidator;

    public OrchestrationActivityService(OrchestrationRunRepository runRepository,
                                        AuditEventRepository auditRepository,
                                        OrchestrationArtifactRepository artifactRepository,
                                        TaskExecutionAgent taskExecutionAgent,
                                        OrchestrationTaskStateService taskStateService,
                                        ProposalValidator proposalValidator,
                                        IsolatedProjectValidator isolatedProjectValidator) {
        this.runRepository = runRepository;
        this.auditRepository = auditRepository;
        this.artifactRepository = artifactRepository;
        this.taskExecutionAgent = taskExecutionAgent;
        this.taskStateService = taskStateService;
        this.proposalValidator = proposalValidator;
        this.isolatedProjectValidator = isolatedProjectValidator;
    }

    @Transactional(readOnly = true)
    public List<TaskBlueprint> loadPlan(String runId) {
        OrchestrationRun run = findRun(runId);
        return run.getTasks().stream()
                .map(task -> new TaskBlueprint(task.getNodeKey(), task.getTitle(), task.getDescription(), task.getDependencies()))
                .toList();
    }

    @Transactional
    public void markRunStarted(String runId) {
        OrchestrationRun run = findRun(runId);
        run.updateExecutionStatus(RunStatus.RUNNING, null);
        auditRepository.save(event(run, "WORKFLOW_STARTED", "Temporal workflow accepted the approved plan"));
    }

    @Transactional
    public void markAwaitingClarification(String runId) {
        OrchestrationRun run = findRun(runId);
        run.awaitClarification();
        auditRepository.save(event(run, "AWAITING_CLARIFICATION", "Paused for human response to requirement questions"));
    }

    @Transactional
    public void recordClarification(String runId, String clarification) {
        OrchestrationRun run = findRun(runId);
        run.recordClarification(clarification);
        auditRepository.save(event(run, "CLARIFICATION_RECORDED", "Human clarification recorded; downstream tasks resumed"));
    }

    public void executeTask(String runId, String taskKey) {
        TaskExecutionContext context = taskStateService.beginTask(runId, taskKey);
        if (context == null) {
            return;
        }
        try {
                    List<ArtifactDraft> drafts = taskExecutionAgent.execute(UUID.fromString(context.runId()), context.requirement(), context.codebaseContext(),
                        context.clarificationNotes(), context.scenario(), context.blueprint());
                    String artifactRoot = GeneratedProjectIdentity.fromRequirement(
                        context.requirement(), context.scenario(), context.codebaseContext()).root();
                    validateArtifacts(drafts, artifactRoot);
                    if (taskKey.equals("final-validation")) {
                        ProposalValidationResult result = proposalValidator.validate(UUID.fromString(runId), drafts, artifactRoot);
                        ProjectExecutionResult execution = isolatedProjectValidator.validate(
                            UUID.fromString(runId), drafts, artifactRoot);
                        drafts = new java.util.ArrayList<>(drafts);
                        String report = result.report() + "\n## Isolated build and test execution\n\n"
                                + execution.summary() + "\n";
                        ArtifactDraft evidence = new ArtifactDraft(
                            artifactRoot + "validation-report.md", "text/markdown", report);
                        drafts.add(evidence);
                        if (!result.passed() || (isolatedProjectValidator.required() && !execution.executed())
                            || (execution.executed() && !execution.passed())) {
                            taskStateService.recordValidationEvidence(runId, evidence);
                                int reportStart = Math.max(0, report.length() - 12000);
                            throw new IllegalStateException("Proposal validation failed; see validation report: "
                                    + report.substring(reportStart));
                        }
                    }
            taskStateService.completeTask(runId, taskKey, drafts);
        } catch (RuntimeException exception) {
            taskStateService.failTask(runId, taskKey, exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void markRunReadyForReview(String runId) {
        OrchestrationRun run = findRun(runId);
        run.updateExecutionStatus(RunStatus.AWAITING_ARTIFACT_REVIEW, null);
        auditRepository.save(event(run, "AWAITING_ARTIFACT_REVIEW", "Generated artifacts are ready for human review"));
    }

    @Transactional
    public void finalizeArtifactReview(String runId, boolean accepted) {
        OrchestrationRun run = findRun(runId);
        run.recordArtifactReview(accepted);
        auditRepository.save(event(run, accepted ? "WORKFLOW_COMPLETED" : "ARTIFACTS_REJECTED",
                accepted ? "Human accepted generated artifacts" : "Human rejected generated artifacts"));
    }

    @Transactional
    public void markRunFailed(String runId, String reason) {
        OrchestrationRun run = findRun(runId);
        run.updateExecutionStatus(RunStatus.FAILED, safeSummary(reason));
        auditRepository.save(event(run, "WORKFLOW_FAILED", safeSummary(reason)));
    }

    @Transactional
    public void acceptArtifacts(String runId, boolean accepted, String actor, String note) {
        OrchestrationRun run = findRun(runId);
        if (run.getStatus() != RunStatus.AWAITING_ARTIFACT_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Artifacts are not ready for review");
        }
        List<OrchestrationArtifact> artifacts = artifactRepository.findByRun_IdOrderByPathAsc(run.getId());
        if (artifacts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run has no generated artifacts");
        }
        if (artifacts.stream().anyMatch(artifact -> artifact.getStatus() != ArtifactStatus.PROPOSED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Artifact decisions have already been recorded");
        }
        artifacts.forEach(artifact -> artifact.setStatus(accepted ? ArtifactStatus.ACCEPTED : ArtifactStatus.REJECTED));
        auditRepository.save(new AuditEvent(UUID.randomUUID(), run,
                accepted ? "ARTIFACTS_ACCEPTED" : "ARTIFACTS_REJECTED", actor,
                note == null ? "" : note, Instant.now()));
        if (!accepted) {
            auditRepository.save(new AuditEvent(UUID.randomUUID(), run, "PROPOSAL_ROLLED_BACK", actor,
                "Rejected proposal artifacts were marked unusable and cannot be exported.", Instant.now()));
        }
    }

    private OrchestrationRun findRun(String runId) {
        return runRepository.findById(UUID.fromString(runId))
                .orElseThrow(() -> new IllegalStateException("Run not found: " + runId));
    }

    private void validateArtifacts(List<ArtifactDraft> drafts, String artifactRoot) {
        if (drafts == null || drafts.isEmpty() || drafts.size() > MAX_ARTIFACTS_PER_TASK) {
            throw new IllegalArgumentException("Agent returned an invalid artifact count");
        }
        Set<String> paths = new HashSet<>();
        for (ArtifactDraft draft : drafts) {
            boolean duplicatePath = draft.path() != null && paths.contains(draft.path());
                boolean allowedPath = draft.path() != null && (draft.path().matches(
                    java.util.regex.Pattern.quote(artifactRoot) + "[A-Za-z0-9._/-]+")
                    || draft.path().equals(artifactRoot + "Dockerfile"));
                if (draft.path() == null || !draft.path().startsWith(artifactRoot)
                    || draft.path().contains("..") || draft.path().contains("\\")
                    || !allowedPath || duplicatePath
                    || !ALLOWED_MEDIA_TYPES.contains(draft.mediaType())
                    || draft.content() == null || draft.content().length() > MAX_ARTIFACT_CHARACTERS) {
                throw new IllegalArgumentException("Agent artifact rejected by policy: path=" + draft.path()
                        + ", mediaType=" + draft.mediaType() + ", duplicate=" + duplicatePath
                        + ", allowedPath=" + allowedPath);
            }
            paths.add(draft.path());
        }
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