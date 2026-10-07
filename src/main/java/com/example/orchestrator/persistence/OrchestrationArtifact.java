package com.example.orchestrator.persistence;

import com.example.orchestrator.domain.ArtifactStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestration_artifact")
public class OrchestrationArtifact {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private OrchestrationRun run;

    @Column(name = "task_key", nullable = false, length = 80)
    private String taskKey;

    @Column(name = "artifact_path", nullable = false, length = 500)
    private String path;

    @Column(name = "media_type", nullable = false, length = 120)
    private String mediaType;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ArtifactStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrchestrationArtifact() {
    }

    public OrchestrationArtifact(UUID id, OrchestrationRun run, String taskKey, String path,
                                 String mediaType, String content, ArtifactStatus status) {
        this.id = id;
        this.run = run;
        this.taskKey = taskKey;
        this.path = path;
        this.mediaType = mediaType;
        this.content = content;
        this.status = status;
    }

    public String getTaskKey() { return taskKey; }
    public String getPath() { return path; }
    public String getMediaType() { return mediaType; }
    public String getContent() { return content; }
    public ArtifactStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setContent(String content) { this.content = content; }
    public void setStatus(ArtifactStatus status) { this.status = status; }
}