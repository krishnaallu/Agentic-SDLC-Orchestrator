package com.example.orchestrator.run;

import java.util.UUID;

public record RunClarificationEvent(UUID runId, int planVersion, String answers) {
}