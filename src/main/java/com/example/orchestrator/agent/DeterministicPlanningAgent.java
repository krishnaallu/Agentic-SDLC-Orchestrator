package com.example.orchestrator.agent;

import com.example.orchestrator.application.PlannedAgent;
import com.example.orchestrator.domain.RunScenario;
import com.example.orchestrator.domain.TaskBlueprint;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class DeterministicPlanningAgent implements PlannedAgent {
    @Override
    public List<TaskBlueprint> plan(UUID runId, String requirement, String codebaseContext, RunScenario scenario) {
        String firstKey = switch (scenario) {
            case GREENFIELD -> "requirement-analysis";
            case BROWNFIELD -> "codebase-analysis";
            case AMBIGUOUS -> "ambiguity-analysis";
        };
        String firstTitle = switch (scenario) {
            case GREENFIELD -> "Normalize requirements";
            case BROWNFIELD -> "Inspect impacted code paths";
            case AMBIGUOUS -> "Identify open questions and assumptions";
        };
        String firstDescription = switch (scenario) {
            case GREENFIELD -> "Translate the request into acceptance criteria and explicit constraints.";
            case BROWNFIELD -> "Map the requested behavior to existing modules, APIs, data flows, and tests.";
            case AMBIGUOUS -> "Extract unresolved decisions from the request and identify what requires human input.";
        };

        return List.of(
                new TaskBlueprint(firstKey, firstTitle, firstDescription, List.of()),
                new TaskBlueprint("architecture", "Propose architecture and contracts",
                        "Define API, data model, impacted components, and validation boundaries.", List.of(firstKey)),
                new TaskBlueprint("implementation", "Prepare implementation proposal",
                        "Generate a scoped code proposal only after architecture review.", List.of("architecture")),
                new TaskBlueprint("tests", "Prepare validation plan and tests",
                        "Define focused unit and integration checks for the acceptance criteria.", List.of("architecture")),
                new TaskBlueprint("integration-tests", "Plan integration and API tests",
                    "Define real-database and HTTP API test cases, including expiry, collisions, and concurrency.",
                    List.of("implementation", "tests")),
                new TaskBlueprint("security-review", "Review security and abuse controls",
                    "Assess authentication, authorization, destination policy, abuse limits, privacy, and request controls.",
                    List.of("architecture", "implementation")),
                new TaskBlueprint("documentation", "Prepare supporting documentation",
                        "Document setup, behavior, assumptions, trade-offs, and operational risks.", List.of("architecture")),
                new TaskBlueprint("deployment-readiness", "Review deployment and operations",
                    "Check migrations, health/readiness, secrets, TLS/proxy guidance, monitoring, and rollback steps.",
                    List.of("integration-tests", "security-review", "documentation")),
                new TaskBlueprint("release-readiness", "Review release readiness",
                    "Synchronize implementation, integration, security, documentation, and operations evidence.",
                    List.of("deployment-readiness")),
                new TaskBlueprint("final-validation", "Assemble final validation evidence",
                    "Collect static checks, executed test evidence, risks, assumptions, limitations, and approval summary.",
                    List.of("release-readiness"))
        );
    }
}