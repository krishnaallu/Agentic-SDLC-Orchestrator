package com.example.orchestrator.agent;

public interface StructuredAgentClient {
    String generateJson(String systemPrompt, String userPrompt);
}