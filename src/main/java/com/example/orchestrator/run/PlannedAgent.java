package com.example.orchestrator.run;

import java.util.List;

public interface PlannedAgent {
    List<TaskBlueprint> plan(String requirement, RunScenario scenario);
}