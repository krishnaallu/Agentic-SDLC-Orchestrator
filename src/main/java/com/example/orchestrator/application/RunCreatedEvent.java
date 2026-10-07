package com.example.orchestrator.application;

import java.util.UUID;

public record RunCreatedEvent(UUID runId, int planVersion) {
}