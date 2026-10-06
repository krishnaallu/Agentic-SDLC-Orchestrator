package com.example.orchestrator.run;

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
                new TaskBlueprint("documentation", "Prepare supporting documentation",
                        "Document setup, behavior, assumptions, trade-offs, and operational risks.", List.of("architecture")),
                new TaskBlueprint("release-readiness", "Review release readiness",
                        "Synchronize implementation, test, and documentation results for final human review.",
                        List.of("implementation", "tests", "documentation"))
        );
    }
}