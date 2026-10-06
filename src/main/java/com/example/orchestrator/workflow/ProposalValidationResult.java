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
        report.append("\nGenerated test source is included in the proposal but has not been compiled or executed. "
                + "No generated command or code has been executed by the orchestrator.\n");
        return report.toString();
    }
}