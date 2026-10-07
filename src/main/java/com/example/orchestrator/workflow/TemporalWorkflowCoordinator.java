package com.example.orchestrator.workflow;

import com.example.orchestrator.application.RunCreatedEvent;
import com.example.orchestrator.application.RunDecisionEvent;
import com.example.orchestrator.application.RunArtifactsAcceptedEvent;
import com.example.orchestrator.application.RunClarificationEvent;
import com.example.orchestrator.application.RunReplannedEvent;
import com.example.orchestrator.domain.ArtifactStatus;
import com.example.orchestrator.domain.RunStatus;
import com.example.orchestrator.persistence.AuditEvent;
import com.example.orchestrator.persistence.AuditEventRepository;
import com.example.orchestrator.persistence.OrchestrationArtifactRepository;
import com.example.orchestrator.persistence.OrchestrationRun;
import com.example.orchestrator.persistence.OrchestrationRunRepository;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "orchestrator.temporal", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TemporalWorkflowCoordinator {
    private static final Logger logger = LoggerFactory.getLogger(TemporalWorkflowCoordinator.class);

    private final WorkflowClient workflowClient;
    private final String taskQueue;
    private final OrchestrationRunRepository runRepository;
    private final AuditEventRepository auditRepository;
    private final OrchestrationArtifactRepository artifactRepository;
    private final boolean reconciliationEnabled;

    public TemporalWorkflowCoordinator(WorkflowClient workflowClient,
                                       @Value("${orchestrator.temporal.task-queue}") String taskQueue,
                                       OrchestrationRunRepository runRepository,
                                       AuditEventRepository auditRepository,
                                       OrchestrationArtifactRepository artifactRepository,
                                       @Value("${orchestrator.temporal.reconcile.enabled:true}") boolean reconciliationEnabled) {
        this.workflowClient = workflowClient;
        this.taskQueue = taskQueue;
        this.runRepository = runRepository;
        this.auditRepository = auditRepository;
        this.artifactRepository = artifactRepository;
        this.reconciliationEnabled = reconciliationEnabled;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void startWorkflow(RunCreatedEvent event) {
        startWorkflow(event.runId().toString(), event.planVersion());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void replaceWorkflowForReplan(RunReplannedEvent event) {
        try {
            workflowClient.newUntypedWorkflowStub(workflowId(event.runId().toString(), event.previousPlanVersion()))
                    .cancel();
        } catch (RuntimeException exception) {
            logger.debug("Previous plan workflow was already closed for run {}", event.runId());
        }
        startWorkflow(event.runId().toString(), event.planVersion());
    }

    @Scheduled(fixedDelayString = "${orchestrator.temporal.reconcile-delay:10000}", initialDelay = 10000)
    public void reconcilePersistedWorkflowState() {
        if (!reconciliationEnabled) {
            return;
        }
        List<RunStatus> resumable = List.of(RunStatus.AWAITING_APPROVAL, RunStatus.APPROVED, RunStatus.REJECTED,
            RunStatus.RUNNING, RunStatus.AWAITING_CLARIFICATION, RunStatus.AWAITING_ARTIFACT_REVIEW);
        for (OrchestrationRun run : runRepository.findByStatusIn(resumable)) {
            startWorkflow(run.getId().toString(), run.getPlanVersion());
            if (run.getStatus() == RunStatus.APPROVED) {
                signalPlanDecision(run.getId().toString(), run.getPlanVersion(), true);
            } else if (run.getStatus() == RunStatus.REJECTED
                    && auditRepository.findByRun_IdAndActionOrderByHappenedAtDesc(run.getId(), "PLAN_REJECTED").size() > 0) {
                signalPlanDecision(run.getId().toString(), run.getPlanVersion(), false);
            } else if (run.getStatus() == RunStatus.AWAITING_CLARIFICATION) {
                auditRepository.findByRun_IdAndActionOrderByHappenedAtDesc(run.getId(), "CLARIFICATION_SUBMITTED")
                        .stream().findFirst().ifPresent(event -> signalClarification(run.getId().toString(), run.getPlanVersion(), event));
                    } else if (run.getStatus() == RunStatus.AWAITING_ARTIFACT_REVIEW) {
                List<ArtifactStatus> statuses = artifactRepository.findByRun_IdOrderByPathAsc(run.getId()).stream()
                        .map(artifact -> artifact.getStatus()).toList();
                if (!statuses.isEmpty() && statuses.stream().noneMatch(status -> status == ArtifactStatus.PROPOSED)) {
                    signalArtifactDecision(run.getId().toString(), run.getPlanVersion(),
                            statuses.stream().allMatch(status -> status == ArtifactStatus.ACCEPTED));
                }
            }
        }
    }

    private void startWorkflow(String runId, int planVersion) {
        String workflowId = workflowId(runId, planVersion);
        OrchestrationWorkflow workflow = workflowClient.newWorkflowStub(OrchestrationWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId(workflowId).setTaskQueue(taskQueue).build());
        try {
            WorkflowClient.start(workflow::execute, runId);
        } catch (WorkflowExecutionAlreadyStarted ignored) {
            logger.debug("Workflow already exists for {}", workflowId);
        } catch (RuntimeException exception) {
            logger.warn("Could not start workflow {}; persisted state will be retried", workflowId, exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void signalDecision(RunDecisionEvent event) {
        signalPlanDecision(event.runId().toString(), event.planVersion(), event.approved());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void signalArtifactDecision(RunArtifactsAcceptedEvent event) {
        signalArtifactDecision(event.runId().toString(), event.planVersion(), event.accepted());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void signalClarification(RunClarificationEvent event) {
        signalClarification(event.runId().toString(), event.planVersion(), event.answers());
    }

    private void signalPlanDecision(String runId, int version, boolean approved) {
        try {
            workflow(runId, version).recordDecision(approved);
        } catch (RuntimeException exception) {
            logger.warn("Could not signal plan decision for run {}; persisted state will be retried", runId, exception);
        }
    }

    private void signalArtifactDecision(String runId, int version, boolean accepted) {
        try {
            workflow(runId, version).recordArtifactDecision(accepted);
        } catch (RuntimeException exception) {
            logger.warn("Could not signal artifact decision for run {}; persisted state will be retried", runId, exception);
        }
    }

    private void signalClarification(String runId, int version, AuditEvent event) {
        signalClarification(runId, version, event.getDetails());
    }

    private void signalClarification(String runId, int version, String answers) {
        try {
            workflow(runId, version).recordClarification(answers);
        } catch (RuntimeException exception) {
            logger.warn("Could not signal clarification for run {}; persisted state will be retried", runId, exception);
        }
    }

    private OrchestrationWorkflow workflow(String runId, int version) {
        return workflowClient.newWorkflowStub(OrchestrationWorkflow.class, workflowId(runId, version));
    }

    static String workflowId(String runId, int planVersion) {
        return "engineering-run-" + runId + "-plan-" + planVersion;
    }
}