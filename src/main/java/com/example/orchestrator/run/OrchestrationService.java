package com.example.orchestrator.run;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrchestrationService {
    private final OrchestrationRunRepository runRepository;
    private final AuditEventRepository auditRepository;
    private final PlannedAgent plannedAgent;

    public OrchestrationService(OrchestrationRunRepository runRepository,
                                AuditEventRepository auditRepository,
                                PlannedAgent plannedAgent) {
        this.runRepository = runRepository;
        this.auditRepository = auditRepository;
        this.plannedAgent = plannedAgent;
    }

    @Transactional
    public OrchestrationRunResponse createRun(CreateRunRequest request) {
        UUID runId = UUID.randomUUID();
        OrchestrationRun run = new OrchestrationRun(runId, request.requirement().trim(),
                request.scenario(), RunStatus.AWAITING_APPROVAL);
        List<TaskBlueprint> plan = plannedAgent.plan(request.requirement().trim(), request.scenario());
        validatePlan(plan);
        for (int index = 0; index < plan.size(); index++) {
            TaskBlueprint blueprint = plan.get(index);
            run.addTask(new OrchestrationTask(UUID.randomUUID(), blueprint.nodeKey(), blueprint.title(),
                    blueprint.description(), blueprint.dependencies(), TaskStatus.PLANNED, index));
        }

        runRepository.saveAndFlush(run);
        auditRepository.save(new AuditEvent(UUID.randomUUID(), run, "RUN_CREATED", "system",
                "Deterministic plan created; no code has been generated or applied.", Instant.now()));
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
        return toResponse(run);
    }

    private OrchestrationRun findRun(UUID runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
    }

    private OrchestrationRunResponse toResponse(OrchestrationRun run) {
        List<OrchestrationRunResponse.TaskResponse> tasks = run.getTasks().stream()
                .map(task -> new OrchestrationRunResponse.TaskResponse(task.getNodeKey(), task.getTitle(),
                        task.getDescription(), task.getDependencies(), task.getStatus()))
                .toList();
        List<OrchestrationRunResponse.AuditEventResponse> audit = auditRepository
                .findByRun_IdOrderByHappenedAtAsc(run.getId()).stream()
                .map(event -> new OrchestrationRunResponse.AuditEventResponse(event.getAction(), event.getActor(),
                        event.getDetails(), event.getHappenedAt()))
                .toList();
        return new OrchestrationRunResponse(run.getId(), run.getRequirement(), run.getScenario(), run.getStatus(),
                run.getDecisionActor(), run.getDecisionNote(), run.getCreatedAt(), run.getUpdatedAt(), tasks, audit);
    }

    private void validatePlan(List<TaskBlueprint> plan) {
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
    }
}