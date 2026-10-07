package com.example.orchestrator.application;

import com.example.orchestrator.domain.RunScenario;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedProjectIdentityTests {
    @Test
    void keepsTheUrlShortenerSampleSlugStable() {
        assertThat(GeneratedProjectIdentity.fromRequirement(
                "Build a URL shortener with expiring links and click analytics").root())
                .isEqualTo("generated/url-shortener/");
    }

    @Test
    void acceptsCommonUrlShortenerMisspelling() {
        assertThat(GeneratedProjectIdentity.fromRequirement(
                "Generate a URL shortner service", RunScenario.GREENFIELD, null).root())
                .isEqualTo("generated/url-shortener/");
    }

    @Test
    void derivesAStableSlugForAnInventoryService() {
        assertThat(GeneratedProjectIdentity.fromRequirement(
                "Build an inventory service with products, stock adjustments, and a REST API").root())
                .isEqualTo("generated/inventory/");
    }

    @Test
    void producesASafeFallbackSlugWhenNoProductNameCanBeExtracted() {
        assertThat(GeneratedProjectIdentity.fromRequirement("Please build this").root())
                .isEqualTo("generated/please-this/");
    }

    @Test
    void brownfieldUrlShortenerSnapshotKeepsUrlShortenerProjectRoot() {
        assertThat(GeneratedProjectIdentity.fromRequirement("Add analytics", RunScenario.BROWNFIELD,
                "### src/main/java/LinkService.java")).isEqualTo(new GeneratedProjectIdentity("url-shortener"));
    }
}
