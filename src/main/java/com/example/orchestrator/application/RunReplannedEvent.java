package com.example.orchestrator.application;

import java.util.UUID;

public record RunReplannedEvent(UUID runId, int previousPlanVersion, int planVersion) {
}