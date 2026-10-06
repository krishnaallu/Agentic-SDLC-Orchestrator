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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

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

    @PostMapping("/{runId}/replan")
    public OrchestrationRunResponse replan(@PathVariable UUID runId,
                                           @Valid @RequestBody UpdateRequirementRequest request) {
        return orchestrationService.replan(runId, request);
    }

    @PostMapping("/{runId}/clarification")
    public OrchestrationRunResponse recordClarification(@PathVariable UUID runId,
                                                        @Valid @RequestBody ClarificationRequest request) {
        return orchestrationService.recordClarification(runId, request);
    }

    @PostMapping("/{runId}/decision")
    public OrchestrationRunResponse recordDecision(@PathVariable UUID runId,
                                                  @Valid @RequestBody RecordDecisionRequest request) {
        return orchestrationService.recordDecision(runId, request);
    }

    @PostMapping("/{runId}/artifacts/decision")
    public OrchestrationRunResponse recordArtifactDecision(@PathVariable UUID runId,
                                                           @Valid @RequestBody ArtifactDecisionRequest request) {
        return orchestrationService.recordArtifactDecision(runId, request);
    }

    @GetMapping(value = "/{runId}/artifacts/export", produces = "application/zip")
    public ResponseEntity<byte[]> exportAcceptedArtifacts(@PathVariable UUID runId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=approved-artifacts-" + runId + ".zip")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(orchestrationService.exportAcceptedArtifacts(runId));
    }

    @GetMapping("/metrics/reliability")
    public ReliabilityMetricsResponse reliabilityMetrics() {
        return orchestrationService.reliabilityMetrics();
    }
}