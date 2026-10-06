package com.example.orchestrator.run;

public enum RunStatus {
    AWAITING_APPROVAL,
    APPROVED,
    REJECTED,
    RUNNING,
    AWAITING_CLARIFICATION,
    AWAITING_ARTIFACT_REVIEW,
    ARTIFACTS_REJECTED,
    COMPLETED,
    FAILED
}