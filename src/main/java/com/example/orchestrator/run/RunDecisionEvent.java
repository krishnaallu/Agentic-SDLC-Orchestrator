package com.example.orchestrator.run;

import java.util.UUID;

public record RunDecisionEvent(UUID runId, int planVersion, boolean approved) {
}