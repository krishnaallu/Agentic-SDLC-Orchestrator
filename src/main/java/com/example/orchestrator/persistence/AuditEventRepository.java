package com.example.orchestrator.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findByRun_IdOrderByHappenedAtAsc(UUID runId);
    List<AuditEvent> findByActionOrderByHappenedAtAsc(String action);
    List<AuditEvent> findByRun_IdAndActionOrderByHappenedAtDesc(UUID runId, String action);
    long countByAction(String action);
}