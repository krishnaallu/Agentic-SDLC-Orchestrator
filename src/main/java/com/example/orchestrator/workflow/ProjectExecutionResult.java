package com.example.orchestrator.workflow;

public record ProjectExecutionResult(boolean executed, boolean passed, int exitCode, String summary) {
    public static ProjectExecutionResult skipped(String reason) {
        return new ProjectExecutionResult(false, false, -1, reason);
    }
}