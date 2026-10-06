package com.example.orchestrator.run;

import java.util.UUID;

public record RunReplannedEvent(UUID runId, int previousPlanVersion, int planVersion) {
}