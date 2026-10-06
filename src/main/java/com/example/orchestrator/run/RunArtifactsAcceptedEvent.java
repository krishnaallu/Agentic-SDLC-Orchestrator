package com.example.orchestrator.run;

import java.util.UUID;

public record RunArtifactsAcceptedEvent(UUID runId, int planVersion, boolean accepted) {
}