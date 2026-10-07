package com.example.orchestrator.application;

import org.junit.jupiter.api.Test;
import com.example.orchestrator.domain.RunScenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportedRequirementPolicyTests {
    private final SupportedRequirementPolicy policy = new SupportedRequirementPolicy();

    @Test
    void acceptsExplicitUrlShortenerRequirements() {
        assertThatCode(() -> policy.requireSupported(
            "Build a URL shortener with expiry and analytics", RunScenario.GREENFIELD, null))
            .doesNotThrowAnyException();
    }

    @Test
    void acceptsBrownfieldRedirectChangesWhenContextIdentifiesTheSupportedSample() {
        assertThatCode(() -> policy.requireSupported(
            "Add analytics without changing redirects", RunScenario.BROWNFIELD,
            "Existing LinkService handles redirects"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsInventoryInsteadOfSilentlyProducingUrlShortenerArtifacts() {
        assertThatThrownBy(() -> policy.requireSupported(
            "Build an inventory service with products and stock adjustments", RunScenario.GREENFIELD,
            "Existing LinkService handles redirects"))
                .isInstanceOf(UnsupportedRequirementException.class)
                .hasMessageContaining("URL-shortener", "No run was created");
    }

    @Test
    void permitsOtherDomainsWhenTheGenericProviderIsEnabled() {
        assertThatCode(() -> policy.requireSupported(
                "Build an inventory service with products and stock adjustments",
                RunScenario.GREENFIELD, null, true)).doesNotThrowAnyException();
    }
}