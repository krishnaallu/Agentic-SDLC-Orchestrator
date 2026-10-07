package com.example.orchestrator.workflow;

import java.util.List;

public record ProposalValidationResult(boolean passed, List<String> checks) {
    public ProposalValidationResult {
        checks = List.copyOf(checks);
    }

    public String report() {
        StringBuilder report = new StringBuilder("# Proposal validation\n\n")
                .append("Static validation result: ").append(passed ? "PASS" : "FAIL").append("\n\n");
        checks.forEach(check -> report.append("- ").append(check).append("\n"));
        report.append("\nThese checks inspect proposal content only. Dynamic build/test execution, if enabled, is reported separately "
            + "by the isolated Docker validation stage. No artifact is copied into the active orchestrator project.\n");
        return report.toString();
    }
}