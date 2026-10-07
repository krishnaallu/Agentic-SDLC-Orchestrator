package com.example.orchestrator.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrchestrationArtifactRepository extends JpaRepository<OrchestrationArtifact, UUID> {
    List<OrchestrationArtifact> findByRun_IdOrderByPathAsc(UUID runId);
    boolean existsByRun_IdAndPath(UUID runId, String path);
}