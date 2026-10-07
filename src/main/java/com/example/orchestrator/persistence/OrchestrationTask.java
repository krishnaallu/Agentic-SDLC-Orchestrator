package com.example.orchestrator.persistence;

import com.example.orchestrator.domain.TaskStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestration_task")
public class OrchestrationTask {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private OrchestrationRun run;

    @Column(name = "node_key", nullable = false, length = 80)
    private String nodeKey;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private List<String> dependencies;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskStatus status;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "output_summary", columnDefinition = "text")
    private String outputSummary;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected OrchestrationTask() {
    }

    public OrchestrationTask(UUID id, String nodeKey, String title, String description,
                             List<String> dependencies, TaskStatus status, int sequenceNumber) {
        this.id = id;
        this.nodeKey = nodeKey;
        this.title = title;
        this.description = description;
        this.dependencies = List.copyOf(dependencies);
        this.status = status;
        this.sequenceNumber = sequenceNumber;
    }

    void setRun(OrchestrationRun run) { this.run = run; }

    public void markRunning() {
        status = TaskStatus.RUNNING;
        attemptCount++;
    }

    public void markSucceeded(String summary) {
        status = TaskStatus.SUCCEEDED;
        outputSummary = summary;
        completedAt = Instant.now();
    }

    public void markFailed(String summary) {
        status = TaskStatus.FAILED;
        outputSummary = summary;
    }

    public UUID getId() { return id; }
    public String getNodeKey() { return nodeKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getDependencies() { return dependencies; }
    public TaskStatus getStatus() { return status; }
    public int getSequenceNumber() { return sequenceNumber; }
    public int getAttemptCount() { return attemptCount; }
    public String getOutputSummary() { return outputSummary; }
    public Instant getCompletedAt() { return completedAt; }
}