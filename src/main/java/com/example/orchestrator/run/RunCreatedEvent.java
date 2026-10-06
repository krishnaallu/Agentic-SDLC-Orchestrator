package com.example.orchestrator.run;

import java.util.UUID;

public record RunCreatedEvent(UUID runId, int planVersion) {
}