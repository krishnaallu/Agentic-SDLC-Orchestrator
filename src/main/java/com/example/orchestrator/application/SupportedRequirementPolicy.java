package com.example.orchestrator.application;

import com.example.orchestrator.domain.RunScenario;
import org.springframework.stereotype.Component;

@Component
public class SupportedRequirementPolicy {
    public void requireSupported(String requirement, RunScenario scenario, String codebaseContext) {
        requireSupported(requirement, scenario, codebaseContext, false);
    }

    public void requireSupported(String requirement, RunScenario scenario, String codebaseContext,
                                 boolean genericProviderEnabled) {
        if (genericProviderEnabled || GeneratedProjectIdentity.supportsUrlShortenerTemplate(
                requirement, codebaseContext, scenario)) {
            return;
        }
        throw new UnsupportedRequirementException(
                "The built-in deterministic generator currently supports URL-shortener requirements only. "
                        + "No run was created. To generate another domain, configure the OpenAI-compatible provider "
                        + "or add a matching domain generator and validator.");
    }
}