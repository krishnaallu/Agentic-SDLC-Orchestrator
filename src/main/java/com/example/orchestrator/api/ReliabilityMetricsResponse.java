package com.example.orchestrator.api;

public record ReliabilityMetricsResponse(
        long totalRuns,
        long successfulRuns,
        long failedRuns,
        double successRate,
        long retryCount,
        long rollbackCount,
        long meanRecoveryMillis,
        double averageEndToEndLatencyMillis
) {
}