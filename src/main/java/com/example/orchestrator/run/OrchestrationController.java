package com.example.orchestrator.run;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/runs")
public class OrchestrationController {
    private final OrchestrationService orchestrationService;

    public OrchestrationController(OrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrchestrationRunResponse createRun(@Valid @RequestBody CreateRunRequest request) {
        return orchestrationService.createRun(request);
    }

    @GetMapping("/{runId}")
    public OrchestrationRunResponse getRun(@PathVariable UUID runId) {
        return orchestrationService.getRun(runId);
    }

    @PostMapping("/{runId}/decision")
    public OrchestrationRunResponse recordDecision(@PathVariable UUID runId,
                                                  @Valid @RequestBody RecordDecisionRequest request) {
        return orchestrationService.recordDecision(runId, request);
    }
}