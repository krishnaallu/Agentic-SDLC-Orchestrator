package com.example.orchestrator.application;

import java.util.UUID;

public record RunClarificationEvent(UUID runId, int planVersion, String answers) {
}