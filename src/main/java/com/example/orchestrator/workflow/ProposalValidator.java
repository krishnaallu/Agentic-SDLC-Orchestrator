package com.example.orchestrator.workflow;

import com.example.orchestrator.run.ArtifactDraft;
import com.example.orchestrator.run.OrchestrationArtifact;
import com.example.orchestrator.run.OrchestrationArtifactRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ProposalValidator {
    private static final String ROOT = "generated/url-shortener/";
    private static final List<String> REQUIRED_SUFFIXES = List.of(
            "LinkService.java", "LinkController.java", "LinkRepository.java", "LinkServiceTest.java", "README.md",
            "V1__create_short_link.sql");
    private static final List<String> FORBIDDEN_MARKERS = List.of(
            "Runtime.getRuntime()", "new ProcessBuilder", "System.exit(", "getRemoteAddr()",
            "X-Forwarded-For", "java.net.URLClassLoader");

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

        String service = contentFor(paths, contents, "LinkService.java");
        boolean validatesHttp = service.contains("Only absolute HTTP(S) URLs are allowed");
        checks.add((validatesHttp ? "PASS" : "FAIL") + " URL destination policy is enforced");
        passed &= validatesHttp;

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