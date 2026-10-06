package com.example.orchestrator.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRun, UUID> {
}