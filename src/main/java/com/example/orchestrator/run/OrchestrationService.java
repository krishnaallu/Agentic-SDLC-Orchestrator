package com.example.orchestrator.run;

import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.example.orchestrator.workflow.OrchestrationActivityService;
import java.time.Instant;

@Service
public class OrchestrationService {
    private final OrchestrationRunRepository runRepository;
    private final AuditEventRepository auditRepository;
    private final PlannedAgent plannedAgent;
    private final ApplicationEventPublisher eventPublisher;
    private final OrchestrationArtifactRepository artifactRepository;
    private final OrchestrationActivityService activityService;

    public OrchestrationService(OrchestrationRunRepository runRepository,
                                AuditEventRepository auditRepository,
                                PlannedAgent plannedAgent,
                                ApplicationEventPublisher eventPublisher,
                                OrchestrationArtifactRepository artifactRepository,
                                OrchestrationActivityService activityService) {
        this.runRepository = runRepository;
        this.auditRepository = auditRepository;
        this.plannedAgent = plannedAgent;
        this.eventPublisher = eventPublisher;
        this.artifactRepository = artifactRepository;
        this.activityService = activityService;
    }

    @Transactional
    public OrchestrationRunResponse createRun(CreateRunRequest request) {
        UUID runId = UUID.randomUUID();
        OrchestrationRun run = new OrchestrationRun(runId, request.requirement().trim(),
            request.codebaseContext(), request.scenario(), RunStatus.AWAITING_APPROVAL);
        runRepository.saveAndFlush(run);
        List<TaskBlueprint> plan = plannedAgent.plan(runId, request.requirement().trim(), request.codebaseContext(), request.scenario());
        validatePlan(plan, request.scenario());
        for (int index = 0; index < plan.size(); index++) {
            TaskBlueprint blueprint = plan.get(index);
            run.addTask(new OrchestrationTask(UUID.randomUUID(), blueprint.nodeKey(), blueprint.title(),
                    blueprint.description(), blueprint.dependencies(), TaskStatus.PLANNED, index));
        }

        runRepository.saveAndFlush(run);
        auditRepository.save(new AuditEvent(UUID.randomUUID(), run, "RUN_CREATED", "system",
                "Deterministic plan created; no code has been generated or applied.", Instant.now()));
        eventPublisher.publishEvent(new RunCreatedEvent(runId, run.getPlanVersion()));
        return toResponse(run);
    }

    @Transactional
    public OrchestrationRunResponse replan(UUID runId, UpdateRequirementRequest request) {
        OrchestrationRun run = findRun(runId);
        int previousPlanVersion = run.getPlanVersion();
        String updatedRequirement = request.requirement().trim();
        run.prepareForReplan(updatedRequirement);
        runRepository.flush();
        List<TaskBlueprint> plan = plannedAgent.plan(runId, updatedRequirement, run.getCodebaseContext(), run.getScenario());
        validatePlan(plan, run.getScenario());
        for (int index = 0; index < plan.size(); index++) {
            TaskBlueprint blueprint = plan.get(index);
            run.addTask(new OrchestrationTask(UUID.randomUUID(), blueprint.nodeKey(), blueprint.title(),
                    blueprint.description(), blueprint.dependencies(), TaskStatus.PLANNED, index));
        }
        artifactRepository.deleteAll(artifactRepository.findByRun_IdOrderByPathAsc(runId));
        runRepository.saveAndFlush(run);
        auditRepository.save(new AuditEvent(UUID.randomUUID(), run, "PLAN_REVISED", "requester",
                "Requirement changed; previous artifacts discarded and approval reset for plan version " + run.getPlanVersion(),
                Instant.now()));
        eventPublisher.publishEvent(new RunReplannedEvent(runId, previousPlanVersion, run.getPlanVersion()));
        return toResponse(run);
    }

    @Transactional(readOnly = true)
    public OrchestrationRunResponse getRun(UUID runId) {
        return toResponse(findRun(runId));
    }

    @Transactional
    public OrchestrationRunResponse recordDecision(UUID runId, RecordDecisionRequest request) {
        OrchestrationRun run = findRun(runId);
        RunStatus decision = request.approved() ? RunStatus.APPROVED : RunStatus.REJECTED;
        try {
            run.recordDecision(decision, request.actor().trim(), request.note());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
        auditRepository.save(new AuditEvent(UUID.randomUUID(), run,
                request.approved() ? "PLAN_APPROVED" : "PLAN_REJECTED", request.actor().trim(),
                request.note() == null ? "" : request.note(), Instant.now()));
        eventPublisher.publishEvent(new RunDecisionEvent(runId, run.getPlanVersion(), request.approved()));
        return toResponse(run);
    }

    @Transactional
    public OrchestrationRunResponse recordArtifactDecision(UUID runId, ArtifactDecisionRequest request) {
        activityService.acceptArtifacts(runId.toString(), request.approved(), request.actor().trim(), request.note());
        if (request.approved()) {
            eventPublisher.publishEvent(new RunArtifactsAcceptedEvent(runId, findRun(runId).getPlanVersion(), true));
        } else {
            eventPublisher.publishEvent(new RunArtifactsAcceptedEvent(runId, findRun(runId).getPlanVersion(), false));
        }
        return toResponse(findRun(runId));
    }

    @Transactional
    public OrchestrationRunResponse recordClarification(UUID runId, ClarificationRequest request) {
        OrchestrationRun run = findRun(runId);
        if (run.getStatus() != RunStatus.AWAITING_CLARIFICATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Run is not awaiting clarification");
        }
        auditRepository.save(new AuditEvent(UUID.randomUUID(), run, "CLARIFICATION_SUBMITTED", request.actor().trim(),
                request.answers().trim(), Instant.now()));
        eventPublisher.publishEvent(new RunClarificationEvent(runId, run.getPlanVersion(), request.answers().trim()));
        return toResponse(run);
    }

    @Transactional
    public List<OrchestrationRunResponse.ArtifactResponse> getAcceptedArtifacts(UUID runId) {
        findRun(runId);
        return artifactRepository.findByRun_IdOrderByPathAsc(runId).stream()
                .filter(artifact -> artifact.getStatus() == com.example.orchestrator.run.ArtifactStatus.ACCEPTED)
                .map(artifact -> new OrchestrationRunResponse.ArtifactResponse(artifact.getTaskKey(),
                        artifact.getPath(), artifact.getMediaType(), artifact.getContent(), artifact.getStatus(),
                        artifact.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportAcceptedArtifacts(UUID runId) {
        findRun(runId);
        List<OrchestrationArtifact> accepted = artifactRepository.findByRun_IdOrderByPathAsc(runId).stream()
                .filter(artifact -> artifact.getStatus() == com.example.orchestrator.run.ArtifactStatus.ACCEPTED)
                .toList();
        if (accepted.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No human-approved artifacts are available to export");
        }
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(buffer)) {
            for (OrchestrationArtifact artifact : accepted) {
                String path = artifact.getPath().substring("generated/url-shortener/".length());
                zip.putNextEntry(new ZipEntry(path));
                zip.write(artifact.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.finish();
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create artifact archive", exception);
        }
    }

    private OrchestrationRun findRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private OrchestrationRunResponse toResponse(OrchestrationRun run) {
        List<OrchestrationRunResponse.TaskResponse> tasks = run.getTasks().stream()
                .map(task -> new OrchestrationRunResponse.TaskResponse(task.getNodeKey(), task.getTitle(),
                    task.getDescription(), task.getDependencies(), task.getStatus(), task.getAttemptCount(),
                    task.getOutputSummary(), task.getCompletedAt()))
                .toList();
        List<OrchestrationRunResponse.AuditEventResponse> audit = auditRepository
                .findByRun_IdOrderByHappenedAtAsc(run.getId()).stream()
                .map(event -> new OrchestrationRunResponse.AuditEventResponse(event.getAction(), event.getActor(),
                        event.getDetails(), event.getHappenedAt()))
                .toList();
            List<OrchestrationRunResponse.ArtifactResponse> artifacts = artifactRepository
                .findByRun_IdOrderByPathAsc(run.getId()).stream()
                .map(artifact -> new OrchestrationRunResponse.ArtifactResponse(artifact.getTaskKey(),
                    artifact.getPath(), artifact.getMediaType(), artifact.getContent(), artifact.getStatus(),
                    artifact.getCreatedAt()))
                .toList();
        return new OrchestrationRunResponse(run.getId(), run.getRequirement(), run.getOriginalRequirement(),
            run.getCodebaseContext(), run.getClarificationNotes(), run.getPlanVersion(), run.getScenario(), run.getStatus(),
                run.getDecisionActor(), run.getDecisionNote(), run.getFailureReason(), run.getCreatedAt(),
            run.getUpdatedAt(), run.getStartedAt(), run.getFinishedAt(), latencyMillis(run),
            tasks, audit, artifacts);
    }

        @Transactional(readOnly = true)
        public ReliabilityMetricsResponse reliabilityMetrics() {
        List<OrchestrationRun> runs = runRepository.findAll();
        long completed = runs.stream().filter(run -> run.getStatus() == RunStatus.COMPLETED).count();
        long failed = runs.stream().filter(run -> run.getStatus() == RunStatus.FAILED).count();
        long retries = runs.stream().flatMap(run -> run.getTasks().stream())
            .mapToLong(task -> Math.max(0, task.getAttemptCount() - 1)).sum();
        long rollbackCount = auditRepository.countByAction("PROPOSAL_ROLLED_BACK");
        double successRate = completed + failed == 0 ? 0.0 : (double) completed / (completed + failed);
        long recoveryMillis = runs.stream().mapToLong(OrchestrationRun::getTotalRecoveryMillis).sum();
        long recoveryCount = runs.stream().mapToLong(OrchestrationRun::getRecoveryCount).sum();
        return new ReliabilityMetricsResponse(runs.size(), completed, failed, successRate, retries, rollbackCount,
            recoveryCount == 0 ? 0 : recoveryMillis / recoveryCount,
            runs.stream().mapToLong(this::latencyMillis).filter(value -> value > 0).average().orElse(0));
        }

        private long latencyMillis(OrchestrationRun run) {
        if (run.getStartedAt() == null) {
            return 0;
        }
        Instant end = run.getFinishedAt() == null ? Instant.now() : run.getFinishedAt();
        return java.time.Duration.between(run.getStartedAt(), end).toMillis();
        }

    private void validatePlan(List<TaskBlueprint> plan, RunScenario scenario) {
        if (plan == null || plan.isEmpty()) {
            throw new IllegalStateException("Planner returned an empty task graph");
        }
        List<String> orderedKeys = plan.stream().map(TaskBlueprint::nodeKey).toList();
        if (orderedKeys.stream().distinct().count() != orderedKeys.size()) {
            throw new IllegalStateException("Planner returned duplicate task keys");
        }
        for (int index = 0; index < plan.size(); index++) {
            for (String dependency : plan.get(index).dependencies()) {
                int dependencyIndex = orderedKeys.indexOf(dependency);
                if (dependencyIndex < 0 || dependencyIndex >= index) {
                    throw new IllegalStateException("Task graph contains a missing or forward dependency");
                }
            }
        }
        java.util.Map<String, TaskBlueprint> nodes = plan.stream()
                .collect(java.util.stream.Collectors.toMap(TaskBlueprint::nodeKey, task -> task));
        String entryNode = switch (scenario) {
            case GREENFIELD -> "requirement-analysis";
            case BROWNFIELD -> "codebase-analysis";
            case AMBIGUOUS -> "ambiguity-analysis";
        };
        for (String required : List.of(entryNode, "architecture", "implementation", "tests", "documentation", "release-readiness")) {
            if (!nodes.containsKey(required)) {
                throw new IllegalStateException("Planner omitted required SDLC node: " + required);
            }
        }
        if (!nodes.get("architecture").dependencies().contains(entryNode)
                || !nodes.get("implementation").dependencies().contains("architecture")
                || !nodes.get("tests").dependencies().contains("architecture")
                || !nodes.get("documentation").dependencies().contains("architecture")
                || !nodes.get("release-readiness").dependencies().containsAll(List.of("implementation", "tests", "documentation"))) {
            throw new IllegalStateException("Planner returned a graph that bypasses required SDLC gates");
        }
    }

}