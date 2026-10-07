package com.example.orchestrator.application;

import java.util.UUID;

public record RunDecisionEvent(UUID runId, int planVersion, boolean approved) {
}