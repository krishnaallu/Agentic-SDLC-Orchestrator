package com.example.orchestrator.agent;

import com.example.orchestrator.run.RunScenario;
import com.example.orchestrator.run.TaskBlueprint;
import com.example.orchestrator.run.TaskExecutionAgent;
import com.example.orchestrator.run.TemplateTaskExecutionAgent;
import com.example.orchestrator.run.DeterministicPlanningAgent;
import com.example.orchestrator.workflow.OrchestrationTaskStateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class LanguageModelAgentTests {
    private final StructuredAgentClient client = mock(StructuredAgentClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
        private final OrchestrationTaskStateService stateService = mock(OrchestrationTaskStateService.class);

    @Test
    void parsesStructuredTaskGraph() {
        when(client.generateJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"tasks":[
                                                                                                        {"nodeKey":"requirement-analysis","title":"Analyze","description":"Clarify scope","dependencies":[]},
                                                                                                        {"nodeKey":"architecture","title":"Design","description":"Define contracts","dependencies":["requirement-analysis"]},
                                                                                                        {"nodeKey":"implementation","title":"Implement","description":"Generate proposal","dependencies":["architecture"]},
                                                                                                        {"nodeKey":"tests","title":"Test","description":"Generate tests","dependencies":["architecture"]},
                                                                                                        {"nodeKey":"documentation","title":"Document","description":"Generate docs","dependencies":["architecture"]},
                                                                                                        {"nodeKey":"release-readiness","title":"Review","description":"Review proposal","dependencies":["implementation","tests","documentation"]}
                        ]}
                        """);
        LanguageModelPlannedAgent agent = new LanguageModelPlannedAgent(client, mapper,
                new DeterministicPlanningAgent(), stateService);

        List<TaskBlueprint> plan = agent.plan(UUID.randomUUID(), "Build it", "", RunScenario.GREENFIELD);

        assertThat(plan).extracting(TaskBlueprint::nodeKey).containsExactly("requirement-analysis", "architecture",
                "implementation", "tests", "documentation", "release-readiness");
    }

    @Test
    void fallsBackToDeterministicPlanWhenProviderFails() {
        when(client.generateJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("provider unavailable"));
        LanguageModelPlannedAgent agent = new LanguageModelPlannedAgent(client, mapper,
                new DeterministicPlanningAgent(), stateService);
        UUID runId = UUID.randomUUID();

        List<TaskBlueprint> plan = agent.plan(runId, "Build a URL shortener", "", RunScenario.GREENFIELD);

        assertThat(plan).extracting(TaskBlueprint::nodeKey)
                .contains("requirement-analysis", "architecture", "implementation", "tests", "documentation", "release-readiness");
        verify(stateService).recordAgentFallback(runId.toString(), "planning", "ResourceAccessException");
    }

    @Test
    void rejectsUnsafeGeneratedArtifactPaths() {
        when(client.generateJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"artifacts":[{"path":"../../application.properties","mediaType":"text/plain","content":"unsafe"}]}
                        """);
        TaskExecutionAgent agent = new LanguageModelTaskExecutionAgent(client, mapper,
                new TemplateTaskExecutionAgent(), stateService);

        UUID runId = UUID.randomUUID();
        List<com.example.orchestrator.run.ArtifactDraft> fallback = agent.execute(runId, "Build a service", "", "",
                RunScenario.GREENFIELD, new TaskBlueprint("implementation", "Implement", "Generate code", List.of()));

        assertThat(fallback).isNotEmpty().allSatisfy(artifact ->
                assertThat(artifact.path()).startsWith("generated/url-shortener/"));
        org.mockito.Mockito.verify(stateService).recordAgentFallback(runId.toString(), "implementation", "IllegalStateException");
    }

    @Test
    void prefixesArtifactsIntoTheIsolatedProposalArea() {
        when(client.generateJson(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"artifacts":[{"path":"src/main/java/App.java","mediaType":"text/x-java-source","content":"class App {}"}]}
                        """);
        TaskExecutionAgent agent = new LanguageModelTaskExecutionAgent(client, mapper,
                new TemplateTaskExecutionAgent(), stateService);

        List<com.example.orchestrator.run.ArtifactDraft> artifacts = agent.execute(UUID.randomUUID(), "Build a service", "", "",
                RunScenario.GREENFIELD, new TaskBlueprint("implementation", "Implement", "Generate code", List.of()));

        assertThat(artifacts).singleElement().extracting("path")
                .isEqualTo("generated/url-shortener/src/main/java/App.java");
    }
}