package com.example.orchestrator.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestration_audit_event")
public class AuditEvent {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private OrchestrationRun run;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 200)
    private String actor;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(name = "happened_at", nullable = false)
    private Instant happenedAt;

    protected AuditEvent() {
    }

    public AuditEvent(UUID id, OrchestrationRun run, String action, String actor, String details, Instant happenedAt) {
        this.id = id;
        this.run = run;
        this.action = action;
        this.actor = actor;
        this.details = details;
        this.happenedAt = happenedAt;
    }

    public UUID getId() { return id; }
    public OrchestrationRun getRun() { return run; }
    public String getAction() { return action; }
    public String getActor() { return actor; }
    public String getDetails() { return details; }
    public Instant getHappenedAt() { return happenedAt; }
}