package com.example.orchestrator.api;

import com.example.orchestrator.domain.RunScenario;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/scenarios")
public class ScenarioController {
    @GetMapping
    public List<ScenarioExample> scenarios() {
        return List.of(
                new ScenarioExample(RunScenario.GREENFIELD, "Greenfield URL shortener",
                        "Build a URL shortener API with expiring links, safe HTTP(S) redirects, collision-resistant codes, and aggregate click analytics."),
                new ScenarioExample(RunScenario.BROWNFIELD, "Brownfield analytics enhancement",
                        "Inspect the existing URL shortener and add aggregate click analytics without changing existing redirect behavior or storing visitor IP addresses."),
                new ScenarioExample(RunScenario.AMBIGUOUS, "Ambiguous expiration policy",
                        "Add expiring links to the URL shortener. Clarify whether omitted expiry means never expire, how timezone and past dates behave, and whether expired codes may be reused before proposing implementation."));
    }
}