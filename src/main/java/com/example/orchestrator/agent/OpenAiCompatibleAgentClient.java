package com.example.orchestrator.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "orchestrator.agent", name = "provider", havingValue = "openai-compatible")
public class OpenAiCompatibleAgentClient implements StructuredAgentClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxResponseCharacters;

    public OpenAiCompatibleAgentClient(RestClient.Builder builder, ObjectMapper objectMapper,
                                       @Value("${orchestrator.agent.base-url:https://api.openai.com/v1}") String baseUrl,
                                       @Value("${orchestrator.agent.api-key:}") String apiKey,
                                       @Value("${orchestrator.agent.model:gpt-4.1-mini}") String model,
                                       @Value("${orchestrator.agent.max-response-characters:500000}") int maxResponseCharacters) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("ORCHESTRATOR_AGENT_API_KEY is required when the openai-compatible provider is enabled");
        }
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient = builder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
        this.maxResponseCharacters = maxResponseCharacters;
    }

    @Override
    public String generateJson(String systemPrompt, String userPrompt) {
        JsonNode response = restClient.post()
                .uri(baseUrl + "/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(headers -> headers.setBearerAuth(apiKey))
                .body(Map.of(
                        "model", model,
                        "temperature", 0,
                        "response_format", Map.of("type", "json_object"),
                        "messages", List.of(
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt))))
                .retrieve()
                .body(JsonNode.class);
        JsonNode content = response == null ? null : response.path("choices").path(0).path("message").path("content");
        if (content == null || !content.isTextual() || content.asText().isBlank()) {
            throw new IllegalStateException("Agent provider returned no structured content");
        }
        String json = content.asText();
        if (json.length() > maxResponseCharacters) {
            throw new IllegalStateException("Agent provider response exceeded the configured size limit");
        }
        try {
            objectMapper.readTree(json);
        } catch (Exception exception) {
            throw new IllegalStateException("Agent provider returned malformed JSON", exception);
        }
        return json;
    }
}