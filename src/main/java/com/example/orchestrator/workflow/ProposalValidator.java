package com.example.orchestrator.workflow;

import com.example.orchestrator.application.ArtifactDraft;
import com.example.orchestrator.persistence.OrchestrationArtifact;
import com.example.orchestrator.persistence.OrchestrationArtifactRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ProposalValidator {
    private static final String ROOT = "generated/url-shortener/";
    private static final List<String> REQUIRED_SUFFIXES = List.of(
            "LinkService.java", "LinkController.java", "LinkRepository.java", "LinkServiceTest.java", "README.md",
            "V1__create_short_link.sql", "DestinationPolicy.java", "SecurityConfiguration.java",
            "RateLimitFilter.java", "DestinationPolicyTest.java", "LinkControllerSecurityTest.java",
            "openapi.yaml", "Dockerfile", "compose.yaml",
            "integration-test-plan.md", "security-review.md", "deployment-readiness.md", "final-engineering-summary.md");
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "Runtime.getRuntime()", "new ProcessBuilder", "System.exit(",
            "java.net.URLClassLoader", "visitor_ip", "client_ip", "ip_address");

    private final OrchestrationArtifactRepository artifactRepository;

    public ProposalValidator(OrchestrationArtifactRepository artifactRepository) {
        this.artifactRepository = artifactRepository;
    }

    public ProposalValidationResult validate(UUID runId, List<ArtifactDraft> currentDrafts) {
        List<OrchestrationArtifact> persisted = artifactRepository.findByRun_IdOrderByPathAsc(runId);
        List<String> paths = new ArrayList<>(persisted.stream().map(OrchestrationArtifact::getPath).toList());
        List<ArtifactDraft> drafts = currentDrafts == null ? List.of() : currentDrafts;
        paths.addAll(drafts.stream().map(ArtifactDraft::path).toList());
        List<String> contents = new ArrayList<>(persisted.stream().map(OrchestrationArtifact::getContent).toList());
        contents.addAll(drafts.stream().map(ArtifactDraft::content).toList());
        List<String> checks = new ArrayList<>();
        boolean passed = true;

        for (String required : REQUIRED_SUFFIXES) {
            boolean exists = paths.stream().anyMatch(path -> path.endsWith(required));
            checks.add((exists ? "PASS" : "FAIL") + " required artifact present: " + required);
            passed &= exists;
        }

        String policy = contentFor(paths, contents, "DestinationPolicy.java");
        boolean validatesHttp = policy.contains("http\".equalsIgnoreCase(scheme)")
            && policy.contains("https\".equalsIgnoreCase(scheme)");
        checks.add((validatesHttp ? "PASS" : "FAIL") + " only absolute HTTP(S) destinations are accepted");
        passed &= validatesHttp;

        boolean blocksPrivateNetworks = policy.contains("isLoopbackAddress")
            && policy.contains("isLinkLocalAddress") && policy.contains("metadata.google.internal");
        checks.add((blocksPrivateNetworks ? "PASS" : "FAIL") + " destination policy rejects loopback, link-local, and metadata targets");
        passed &= blocksPrivateNetworks;

        String rateLimiter = contentFor(paths, contents, "RateLimitFilter.java");
        boolean hashesPeerAddress = rateLimiter.contains("HmacSHA256")
            && rateLimiter.contains("getRemoteAddr()")
            && !rateLimiter.contains("X-Forwarded-For")
            && rateLimiter.contains("429") && rateLimiter.contains("8192") && rateLimiter.contains("503");
        checks.add((hashesPeerAddress ? "PASS" : "FAIL") + " rate limiting pseudonymizes direct peer address and ignores untrusted forwarding headers");
        passed &= hashesPeerAddress;

        boolean hasManagementSecurity = paths.stream().anyMatch(path -> path.endsWith("SecurityConfiguration.java"))
            && contents.stream().anyMatch(content -> content.contains("SCOPE_links:write")
                && content.contains("SCOPE_links:read") && content.contains("denyAll()"));
        checks.add((hasManagementSecurity ? "PASS" : "FAIL") + " management routes require explicit JWT scopes and unknown routes are denied");
        passed &= hasManagementSecurity;

        String openApi = contentFor(paths, contents, "openapi.yaml");
        boolean hasApiContract = openApi.contains("openapi: 3.1.0")
            && openApi.contains("/links/{code}") && openApi.contains("/links/{code}/analytics")
            && openApi.contains("bearerAuth");
        checks.add((hasApiContract ? "PASS" : "FAIL") + " OpenAPI contract describes create, redirect, and analytics security");
        passed &= hasApiContract;

        for (String forbidden : FORBIDDEN_MARKERS) {
            boolean absent = contents.stream().noneMatch(content -> content.contains(forbidden));
            checks.add((absent ? "PASS" : "FAIL") + " unsafe construct absent: " + forbidden);
            passed &= absent;
        }

        boolean allScoped = paths.stream().allMatch(path -> path.startsWith(ROOT)
                && !path.contains("..") && !path.contains("\\"));
        checks.add((allScoped ? "PASS" : "FAIL") + " every proposal path remains inside isolated artifact namespace");
        passed &= allScoped;
        return new ProposalValidationResult(passed, checks);
    }

    private String contentFor(List<String> paths, List<String> contents, String suffix) {
        for (int index = 0; index < paths.size(); index++) {
            if (paths.get(index).endsWith(suffix)) {
                return contents.get(index);
            }
        }
        return "";
    }
}