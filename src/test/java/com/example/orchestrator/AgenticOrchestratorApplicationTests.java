package com.example.orchestrator;

import com.example.orchestrator.run.CreateRunRequest;
import com.example.orchestrator.run.ArtifactDecisionRequest;
import com.example.orchestrator.run.OrchestrationRunResponse;
import com.example.orchestrator.run.OrchestrationService;
import com.example.orchestrator.run.RecordDecisionRequest;
import com.example.orchestrator.run.RunScenario;
import com.example.orchestrator.run.RunStatus;
import com.example.orchestrator.workflow.OrchestrationActivityService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AgenticOrchestratorApplicationTests {
	@Autowired
	private OrchestrationService orchestrationService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrchestrationActivityService activityService;

	@Test
	void contextLoads() {
	}

	@Test
	void createsParallelPlanAndWaitsForHumanApproval() {
		OrchestrationRunResponse response = orchestrationService.createRun(
				new CreateRunRequest("Build a URL shortener with analytics", RunScenario.GREENFIELD));

		Map<String, OrchestrationRunResponse.TaskResponse> tasks = response.tasks().stream()
				.collect(Collectors.toMap(OrchestrationRunResponse.TaskResponse::nodeKey, Function.identity()));

		assertThat(response.status()).isEqualTo(RunStatus.AWAITING_APPROVAL);
		assertThat(tasks.keySet()).contains("implementation", "tests", "documentation", "release-readiness");
		assertThat(tasks.get("implementation").dependencies()).containsExactly("architecture");
		assertThat(tasks.get("tests").dependencies()).containsExactly("architecture");
		assertThat(tasks.get("documentation").dependencies()).containsExactly("architecture");
		assertThat(tasks.get("release-readiness").dependencies())
				.containsExactly("implementation", "tests", "documentation");
		assertThat(response.audit()).extracting(OrchestrationRunResponse.AuditEventResponse::action)
				.containsExactly("RUN_CREATED");
	}

	@Test
	void recordsDecisionAndPreventsASecondDecision() {
		OrchestrationRunResponse created = orchestrationService.createRun(
				new CreateRunRequest("Build a URL shortener", RunScenario.GREENFIELD));

		OrchestrationRunResponse approved = orchestrationService.recordDecision(created.id(),
				new RecordDecisionRequest(true, "reviewer@example.test", "Plan looks scoped"));

		assertThat(approved.status()).isEqualTo(RunStatus.APPROVED);
		assertThat(approved.decisionActor()).isEqualTo("reviewer@example.test");
		assertThat(approved.audit()).extracting(OrchestrationRunResponse.AuditEventResponse::action)
				.containsExactly("RUN_CREATED", "PLAN_APPROVED");
		assertThatThrownBy(() -> orchestrationService.recordDecision(created.id(),
				new RecordDecisionRequest(false, "reviewer@example.test", "Change my mind")))
				.isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
	}

	@Test
	void exposesCommandInspectionAndApprovalEndpoints() throws Exception {
		MvcResult createResult = mockMvc.perform(post("/api/v1/runs")
				.contentType("application/json")
				.content("""
						{"requirement":"Build a URL shortener with analytics","scenario":"GREENFIELD"}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
				.andExpect(jsonPath("$.tasks[?(@.nodeKey == 'release-readiness')]").exists())
				.andReturn();
		String runId = JsonPath.read(createResult.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(get("/api/v1/runs/{runId}", runId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requirement").value("Build a URL shortener with analytics"));

		mockMvc.perform(post("/api/v1/runs/{runId}/decision", runId)
				.contentType("application/json")
				.content("""
						{"approved":true,"actor":"reviewer","note":"Plan reviewed"}
						"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("APPROVED"))
				.andExpect(jsonPath("$.audit[1].action").value("PLAN_APPROVED"));
	}

	@Test
	void generatesAndPersistsReviewableUrlShortenerArtifactsAfterPlanApproval() {
		OrchestrationRunResponse created = orchestrationService.createRun(
				new CreateRunRequest("Build a URL shortener with expiring links and click analytics", RunScenario.GREENFIELD));
		orchestrationService.recordDecision(created.id(),
				new RecordDecisionRequest(true, "reviewer", "Plan accepted"));

		activityService.markRunStarted(created.id().toString());
		for (var task : activityService.loadPlan(created.id().toString())) {
			activityService.executeTask(created.id().toString(), task.nodeKey());
		}
		activityService.markRunReadyForReview(created.id().toString());

		OrchestrationRunResponse awaitingReview = orchestrationService.getRun(created.id());
		assertThat(awaitingReview.status()).isEqualTo(RunStatus.AWAITING_ARTIFACT_REVIEW);
		assertThat(awaitingReview.artifacts()).hasSizeGreaterThan(5);
		assertThat(awaitingReview.artifacts()).anySatisfy(artifact -> {
			assertThat(artifact.path()).endsWith("LinkService.java");
			assertThat(artifact.content()).contains("Only absolute HTTP(S) URLs are allowed");
		});
		assertThat(awaitingReview.artifacts()).anySatisfy(artifact -> {
			assertThat(artifact.path()).endsWith("validation-report.md");
			assertThat(artifact.content()).contains("Static validation result: PASS")
					.contains("has not been compiled or executed");
		});
		assertThat(awaitingReview.artifacts()).allSatisfy(artifact ->
				assertThat(artifact.status()).isEqualTo(com.example.orchestrator.run.ArtifactStatus.PROPOSED));

		orchestrationService.recordArtifactDecision(created.id(),
				new ArtifactDecisionRequest(true, "reviewer", "Artifacts reviewed"));
		activityService.finalizeArtifactReview(created.id().toString(), true);
		OrchestrationRunResponse accepted = orchestrationService.getRun(created.id());
		assertThat(accepted.status()).isEqualTo(RunStatus.COMPLETED);
		assertThat(accepted.artifacts()).allSatisfy(artifact ->
				assertThat(artifact.status()).isEqualTo(com.example.orchestrator.run.ArtifactStatus.ACCEPTED));
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(
				orchestrationService.exportAcceptedArtifacts(created.id())))) {
			assertThat(zip.getNextEntry()).isNotNull();
		} catch (java.io.IOException exception) {
			throw new AssertionError(exception);
		}
	}

	@Test
	void exposesAllAssignmentScenarioPresets() throws Exception {
		mockMvc.perform(get("/api/v1/scenarios"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].scenario").value("GREENFIELD"))
				.andExpect(jsonPath("$[1].scenario").value("BROWNFIELD"))
				.andExpect(jsonPath("$[2].scenario").value("AMBIGUOUS"));
	}

	@Test
	void replanningIncrementsPlanVersionAndPreservesOriginalRequest() {
		OrchestrationRunResponse created = orchestrationService.createRun(
				new CreateRunRequest("Add analytics", RunScenario.BROWNFIELD));
		activityService.markRunStarted(created.id().toString());
		activityService.markRunFailed(created.id().toString(), "Simulated validation failure");
		OrchestrationRunResponse revised = orchestrationService.replan(created.id(),
				new com.example.orchestrator.run.UpdateRequirementRequest("Add analytics without storing visitor IPs"));

		assertThat(revised.originalRequirement()).isEqualTo("Add analytics");
		assertThat(revised.requirement()).isEqualTo("Add analytics without storing visitor IPs");
		assertThat(revised.planVersion()).isEqualTo(2);
		assertThat(revised.status()).isEqualTo(RunStatus.AWAITING_APPROVAL);
		assertThat(revised.startedAt()).isNull();
		assertThat(revised.finishedAt()).isNull();
		assertThat(revised.audit()).extracting(OrchestrationRunResponse.AuditEventResponse::action)
				.contains("PLAN_REVISED");
	}

	@Test
	void brownfieldContextIsIncludedInGeneratedAnalysisArtifact() {
		String context = "LinkController delegates to LinkService; LinkRepository stores link rows.";
		OrchestrationRunResponse created = orchestrationService.createRun(new CreateRunRequest(
				"Add click analytics", RunScenario.BROWNFIELD, context));

		activityService.markRunStarted(created.id().toString());
		activityService.executeTask(created.id().toString(), "codebase-analysis");

		OrchestrationRunResponse inspected = orchestrationService.getRun(created.id());
		assertThat(inspected.artifacts()).anySatisfy(artifact -> {
			assertThat(artifact.path()).endsWith("codebase-analysis.md");
			assertThat(artifact.content()).contains(context);
		});
	}

	@Test
	void unapprovedArtifactsCannotBeExported() {
		OrchestrationRunResponse created = orchestrationService.createRun(
				new CreateRunRequest("Build a URL shortener", RunScenario.GREENFIELD));
		activityService.markRunStarted(created.id().toString());
		activityService.executeTask(created.id().toString(), "requirement-analysis");

		assertThatThrownBy(() -> orchestrationService.exportAcceptedArtifacts(created.id()))
				.isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
	}

}
