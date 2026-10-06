package com.example.orchestrator.run;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

public interface OrchestrationRunRepository extends JpaRepository<OrchestrationRun, UUID> {
	List<OrchestrationRun> findByStatusIn(Collection<RunStatus> statuses);
}