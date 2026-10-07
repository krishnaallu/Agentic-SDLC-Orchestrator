package com.example.orchestrator.api;

import com.example.orchestrator.domain.RunScenario;

public record ScenarioExample(RunScenario scenario, String title, String requirement) {
}