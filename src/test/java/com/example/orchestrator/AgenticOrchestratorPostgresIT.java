package com.example.orchestrator;

import com.example.orchestrator.api.CreateRunRequest;
import com.example.orchestrator.application.OrchestrationService;
import com.example.orchestrator.domain.RunScenario;
import com.example.orchestrator.persistence.OrchestrationRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres-integration")
@SpringBootTest
@AutoConfigureMockMvc
class AgenticOrchestratorPostgresIT {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrchestrationService orchestrationService;

    @Autowired
    private OrchestrationRunRepository runRepository;

    @Test
    void persistsAuthenticatedApprovalsAndRejectsInsufficientScope() throws Exception {
        String runId = mockMvc.perform(post("/api/v1/runs")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_orchestrator:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Build a URL shortener; verify PostgreSQL-backed API integration","scenario":"GREENFIELD"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(runId, "$.id");

        mockMvc.perform(post("/api/v1/runs/{runId}/decision", id)
                        .with(jwt().jwt(token -> token.subject("limited-writer"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_orchestrator:write")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"actor\":\"spoofed-actor\",\"note\":\"approve\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/runs/{runId}/decision", id)
                        .with(jwt().jwt(token -> token.subject("postgres-reviewer"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_orchestrator:approve")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"actor\":\"spoofed-actor\",\"note\":\"approved\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionActor").value("postgres-reviewer"))
                .andExpect(jsonPath("$.audit[1].actor").value("postgres-reviewer"));
    }

    @Test
    void concurrentRunCreationPersistsIndependentPlansAndAuditEvents() throws Exception {
        int runCount = 12;
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<java.util.concurrent.Future<java.util.UUID>> futures = new ArrayList<>();
            for (int index = 0; index < runCount; index++) {
                int runNumber = index;
                futures.add(executor.submit(() -> {
                    start.await();
                        return orchestrationService.createRun(new CreateRunRequest(
                            "Build a URL shortener; concurrent PostgreSQL run " + runNumber,
                            RunScenario.GREENFIELD)).id();
                }));
            }
            start.countDown();
            List<java.util.UUID> ids = new ArrayList<>();
            for (var future : futures) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(ids).doesNotHaveDuplicates();
            assertThat(runRepository.findAllById(ids)).hasSize(runCount);
            assertThat(ids.stream().map(orchestrationService::getRun))
                    .allSatisfy(run -> assertThat(run.audit()).extracting("action").contains("RUN_CREATED"));
        }
    }
}