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

    @Version
    @Column(nullable = false)
    private Long version;

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

    public OrchestrationRun(UUID id, String requirement, RunScenario scenario, RunStatus status) {
        this.id = id;
        this.requirement = requirement;
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

    public UUID getId() { return id; }
    public String getRequirement() { return requirement; }
    public RunScenario getScenario() { return scenario; }
    public RunStatus getStatus() { return status; }
    public String getDecisionActor() { return decisionActor; }
    public String getDecisionNote() { return decisionNote; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrchestrationTask> getTasks() { return tasks; }
}