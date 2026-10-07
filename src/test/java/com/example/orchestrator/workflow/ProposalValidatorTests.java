package com.example.orchestrator.workflow;

import com.example.orchestrator.application.ArtifactDraft;
import com.example.orchestrator.persistence.OrchestrationArtifactRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProposalValidatorTests {
    private final OrchestrationArtifactRepository repository = mock(OrchestrationArtifactRepository.class);
    private final ProposalValidator validator = new ProposalValidator(repository);

    @Test
    void validatesGenericInventoryProjectStructureWithoutUrlShortenerRules() {
        UUID runId = UUID.randomUUID();
        when(repository.findByRun_IdOrderByPathAsc(runId)).thenReturn(List.of());
        List<ArtifactDraft> drafts = List.of(
                artifact("pom.xml", "<project></project>"),
                artifact("README.md", "# Inventory Service"),
                artifact("requirements.md", "Inventory service manages products and stock adjustments."),
                artifact("architecture.md", "Inventory architecture provides a product and stock adjustment API."),
                artifact("integration-test-plan.md", "Test product and stock flows."),
                artifact("security-review.md", "Review access control."),
                artifact("deployment-readiness.md", "Review database and secrets."),
                artifact("final-engineering-summary.md", "Evidence summary."),
                artifact("src/main/java/com/example/inventory/InventoryApplication.java", "class InventoryApplication {}"),
                artifact("src/test/java/com/example/inventory/InventoryApplicationTest.java", "class InventoryApplicationTest {}"));

        ProposalValidationResult result = validator.validate(runId, drafts, "generated/inventory/");

        assertThat(result.passed()).isTrue();
        assertThat(result.report()).contains("generic service contains Java application source",
            "generic service contains automated tests", "README.md identifies requested project domain: inventory",
            "requirements.md identifies requested project domain: inventory",
            "architecture.md identifies requested project domain: inventory",
            "generic Java source package/path identifies requested project domain: inventory",
            "generic test package/path identifies requested project domain: inventory",
                "every proposal path remains inside isolated artifact namespace")
                .doesNotContain("DestinationPolicy.java", "short code");
    }

    @Test
    void rejectsGenericArtifactsThatDoNotNameTheRequestedDomain() {
        UUID runId = UUID.randomUUID();
        when(repository.findByRun_IdOrderByPathAsc(runId)).thenReturn(List.of());
        List<ArtifactDraft> drafts = List.of(
                artifact("pom.xml", "<project></project>"),
                artifact("README.md", "# URL Shortener"),
                artifact("requirements.md", "Create short links."),
                artifact("architecture.md", "Link redirect system."),
                artifact("integration-test-plan.md", "test"),
                artifact("security-review.md", "review"),
                artifact("deployment-readiness.md", "ready"),
                artifact("final-engineering-summary.md", "summary"),
                artifact("src/main/java/com/example/urlshortener/App.java", "class App {}"),
                artifact("src/test/java/com/example/urlshortener/AppTest.java", "class AppTest {}"));

        ProposalValidationResult result = validator.validate(runId, drafts, "generated/inventory/");

        assertThat(result.passed()).isFalse();
        assertThat(result.report()).contains("FAIL README.md identifies requested project domain: inventory",
            "FAIL requirements.md identifies requested project domain: inventory",
            "FAIL architecture.md identifies requested project domain: inventory",
            "FAIL generic Java source package/path identifies requested project domain: inventory",
            "FAIL generic test package/path identifies requested project domain: inventory");
    }

    @Test
    void rejectsGenericArtifactsOutsideTheirProjectRoot() {
        UUID runId = UUID.randomUUID();
        when(repository.findByRun_IdOrderByPathAsc(runId)).thenReturn(List.of());

        ProposalValidationResult result = validator.validate(runId, List.of(
                artifact("pom.xml", "<project></project>"),
                artifact("README.md", "Inventory"),
                artifact("requirements.md", "Inventory"),
                artifact("architecture.md", "Inventory"),
                artifact("integration-test-plan.md", "Inventory"),
                artifact("security-review.md", "Inventory"),
                artifact("deployment-readiness.md", "Inventory"),
                artifact("final-engineering-summary.md", "Inventory"),
                artifact("src/main/java/App.java", "class App {}"),
                artifact("src/test/java/AppTest.java", "class AppTest {}"),
                new ArtifactDraft("generated/other/extra.txt", "text/plain", "escaped project root")),
                "generated/inventory/");

        assertThat(result.passed()).isFalse();
        assertThat(result.report()).contains("FAIL every proposal path remains inside isolated artifact namespace");
    }

    private ArtifactDraft artifact(String path, String content) {
        return new ArtifactDraft("generated/inventory/" + path, "text/plain", content);
    }
}