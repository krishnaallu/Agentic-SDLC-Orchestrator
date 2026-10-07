package com.example.orchestrator.application;

import java.util.UUID;

public record RunArtifactsAcceptedEvent(UUID runId, int planVersion, boolean accepted) {
}