package com.example.orchestrator.run;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orchestration_run")
public class OrchestrationRun {
    @Id
    private UUID id;

    @Column(nullable = false, columnDefinition = "text")
    private String requirement;

    @Column(name = "original_requirement", nullable = false, columnDefinition = "text")
    private String originalRequirement;

    @Column(name = "codebase_context", nullable = false, columnDefinition = "text")
    private String codebaseContext;

    @Column(name = "clarification_notes", nullable = false, columnDefinition = "text")
    private String clarificationNotes = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RunStatus status;

    @Column(name = "decision_actor", length = 200)
    private String decisionActor;

    @Column(name = "decision_note", columnDefinition = "text")
    private String decisionNote;

    @Column(name = "plan_version", nullable = false)
    private int planVersion = 1;

    @Column(name = "failure_reason", columnDefinition = "text")
    private String failureReason;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "total_recovery_millis", nullable = false)
    private long totalRecoveryMillis;

    @Column(name = "recovery_count", nullable = false)
    private long recoveryCount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNumber ASC")
    private List<OrchestrationTask> tasks = new ArrayList<>();

    protected OrchestrationRun() {
    }

    public OrchestrationRun(UUID id, String requirement, String codebaseContext,
                            RunScenario scenario, RunStatus status) {
        this.id = id;
        this.requirement = requirement;
        this.originalRequirement = requirement;
        this.codebaseContext = codebaseContext == null ? "" : codebaseContext;
        this.scenario = scenario;
        this.status = status;
    }

    public void addTask(OrchestrationTask task) {
        tasks.add(task);
        task.setRun(this);
    }

    public void recordDecision(RunStatus decision, String actor, String note) {
        if (status != RunStatus.AWAITING_APPROVAL) {
            throw new IllegalStateException("A decision has already been recorded for this run");
        }
        status = decision;
        decisionActor = actor;
        decisionNote = note;
    }

    public void updateExecutionStatus(RunStatus executionStatus, String reason) {
        if (status == RunStatus.REJECTED || status == RunStatus.COMPLETED) {
            throw new IllegalStateException("Run is already in a terminal state");
        }
        status = executionStatus;
        failureReason = reason;
        if (executionStatus == RunStatus.RUNNING && startedAt == null) {
            startedAt = Instant.now();
        }
        if (executionStatus == RunStatus.FAILED || executionStatus == RunStatus.COMPLETED) {
            finishedAt = Instant.now();
        }
    }

    public void replacePlan(String updatedRequirement) {
        if (status == RunStatus.RUNNING || status == RunStatus.AWAITING_ARTIFACT_REVIEW) {
            throw new IllegalStateException("Cannot re-plan while tasks are executing or artifacts are under review");
        }
        requirement = updatedRequirement;
        status = RunStatus.AWAITING_APPROVAL;
        decisionActor = null;
        decisionNote = null;
        failureReason = null;
        planVersion++;
        startedAt = null;
        finishedAt = null;
    }

    public void prepareForReplan(String updatedRequirement) {
        if (status == RunStatus.FAILED && finishedAt != null) {
            totalRecoveryMillis += java.time.Duration.between(finishedAt, Instant.now()).toMillis();
            recoveryCount++;
        }
        replacePlan(updatedRequirement);
        tasks.clear();
    }

    public void recordArtifactReview(boolean accepted) {
        if (status != RunStatus.AWAITING_ARTIFACT_REVIEW) {
            throw new IllegalStateException("Run is not waiting for artifact review");
        }
        status = accepted ? RunStatus.COMPLETED : RunStatus.ARTIFACTS_REJECTED;
        failureReason = accepted ? null : "Generated artifacts were rejected during human review";
        finishedAt = Instant.now();
    }

    public void recordClarification(String notes) {
        if (status != RunStatus.AWAITING_CLARIFICATION) {
            throw new IllegalStateException("Run is not waiting for clarification");
        }
        clarificationNotes = notes;
        status = RunStatus.RUNNING;
    }

    public void awaitClarification() {
        if (status != RunStatus.RUNNING) {
            throw new IllegalStateException("Run is not executing");
        }
        status = RunStatus.AWAITING_CLARIFICATION;
    }

    public UUID getId() { return id; }
    public String getRequirement() { return requirement; }
    public String getOriginalRequirement() { return originalRequirement; }
    public String getCodebaseContext() { return codebaseContext; }
    public String getClarificationNotes() { return clarificationNotes; }
    public int getPlanVersion() { return planVersion; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public long getTotalRecoveryMillis() { return totalRecoveryMillis; }
    public long getRecoveryCount() { return recoveryCount; }
    public RunScenario getScenario() { return scenario; }
    public RunStatus getStatus() { return status; }
    public String getDecisionActor() { return decisionActor; }
    public String getDecisionNote() { return decisionNote; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrchestrationTask> getTasks() { return tasks; }
}