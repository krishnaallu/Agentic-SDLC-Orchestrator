package com.example.orchestrator;

import com.example.orchestrator.run.CreateRunRequest;
import com.example.orchestrator.run.OrchestrationRunResponse;
import com.example.orchestrator.run.OrchestrationService;
import com.example.orchestrator.run.RecordDecisionRequest;
import com.example.orchestrator.run.RunScenario;
import com.example.orchestrator.run.RunStatus;
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

}
