package com.example.orchestrator.run;

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