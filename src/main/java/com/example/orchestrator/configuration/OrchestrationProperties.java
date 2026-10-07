package com.example.orchestrator.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrator")
public record OrchestrationProperties(Temporal temporal) {
    public record Temporal(String target, String taskQueue) {
    }
}