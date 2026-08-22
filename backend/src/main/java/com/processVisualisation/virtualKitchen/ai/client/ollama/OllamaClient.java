package com.processVisualisation.virtualKitchen.ai.client.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.client.AIClient;
import com.processVisualisation.virtualKitchen.ai.config.OllamaProperties;
import com.processVisualisation.virtualKitchen.ai.dto.AIRequest;
import com.processVisualisation.virtualKitchen.ai.dto.AIResponse;
import com.processVisualisation.virtualKitchen.ai.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.ai.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.ai.exception.AITimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(
        prefix = "ai",
        name = "provider",
        havingValue = "ollama"
)
public class OllamaClient implements AIClient {

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaClient(
            @Qualifier("ollamaRestClient") RestClient restClient,
            OllamaProperties properties) {

        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public AIResponse chat(AIRequest request) {

        validateConfiguration();
        validateRequest(request);

        String model = StringUtils.hasText(request.getModel())
                ? request.getModel()
                : properties.getDefaultModel();

        long startedAt = System.nanoTime();

        System.out.println(
                "[AI] Request start. provider=ollama model=" + model
        );

        try {

            String body = objectMapper.writeValueAsString(
                    buildRequestPayload(request, model)
            );

            String rawResponse = restClient.post()
                    .uri(properties.getChatEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            long latencyMs = elapsedMs(startedAt);

            if (!StringUtils.hasText(rawResponse)) {
                System.out.println(
                        "[AI] Failure. provider=ollama " +
                                "reason=empty_response latencyMs=" + latencyMs
                );

                throw new AIInvalidResponseException(
                        "Received empty response from Ollama"
                );
            }

            AIResponse parsed = parseResponse(rawResponse, model);

            System.out.println(
                    "[AI] Success. provider=ollama model=" +
                            parsed.getModel()
                            + " latencyMs=" + latencyMs
                            + " promptTokens=" + safeInt(parsed.getPromptTokens())
                            + " completionTokens=" + safeInt(parsed.getCompletionTokens())
                            + " totalTokens=" + safeInt(parsed.getTotalTokens())
            );

            return parsed;

        } catch (RestClientResponseException ex) {

            long latencyMs = elapsedMs(startedAt);

            System.out.println(
                    "[AI] Failure. provider=ollama " +
                            "reason=http_error status=" +
                            ex.getStatusCode().value()
                            + " latencyMs=" + latencyMs
                            + " body=" + ex.getResponseBodyAsString()
            );

            throw new AICommunicationException(
                    "Ollama API request failed with status: "
                            + ex.getStatusCode(),
                    ex
            );

        } catch (ResourceAccessException ex) {

            long latencyMs = elapsedMs(startedAt);

            System.out.println(
                    "[AI] Failure. provider=ollama " +
                            "reason=timeout_or_connectivity latencyMs="
                            + latencyMs
            );

            throw new AITimeoutException(
                    "Ollama request timed out or could not connect",
                    ex
            );

        } catch (JsonProcessingException ex) {

            long latencyMs = elapsedMs(startedAt);

            System.out.println(
                    "[AI] Failure. provider=ollama " +
                            "reason=invalid_json latencyMs=" + latencyMs
            );

            throw new AIInvalidResponseException(
                    "Failed to process Ollama JSON payload",
                    ex
            );
        }
    }

    private Map<String, Object> buildRequestPayload(
            AIRequest request,
            String model) {

        Map<String, Object> payload = new HashMap<>();

        payload.put("model", model);

        List<Map<String, Object>> messages = new ArrayList<>();

        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(Map.of(
                    "role", "system",
                    "content", request.getSystemPrompt()
            ));
        }

        messages.add(Map.of(
                "role", "user",
                "content", request.getUserPrompt()
        ));

        payload.put("messages", messages);

        // Important: Ollama should return one complete response.
        payload.put("stream", false);

        Map<String, Object> options = new HashMap<>();

        if (request.getTemperature() != null) {
            options.put("temperature", request.getTemperature());
        }

        if (request.getMaxTokens() != null) {
            options.put("num_predict", request.getMaxTokens());
        }

        if (!options.isEmpty()) {
            payload.put("options", options);
        }
        payload.put("think", false);

        return payload;
    }

    private AIResponse parseResponse(
            String rawResponse,
            String requestedModel)
            throws JsonProcessingException {

        JsonNode root = objectMapper.readTree(rawResponse);

        String content = root.path("message")
                .path("content")
                .asText(null);

        if (!StringUtils.hasText(content)) {
            throw new AIInvalidResponseException(
                    "Ollama response does not contain message content"
            );
        }

        return AIResponse.builder()
                .content(content)
                .model(root.path("model").asText(requestedModel))
                .promptTokens(
                        readInt(root, "prompt_eval_count")
                )
                .completionTokens(
                        readInt(root, "eval_count")
                )
                .totalTokens(
                        calculateTotalTokens(root)
                )
                .finishReason(
                        root.path("done_reason").asText(null)
                )
                .rawResponse(rawResponse)
                .build();
    }

    private Integer calculateTotalTokens(JsonNode root) {

        Integer prompt = readInt(root, "prompt_eval_count");
        Integer completion = readInt(root, "eval_count");

        if (prompt == null && completion == null) {
            return null;
        }

        return (prompt == null ? 0 : prompt)
                + (completion == null ? 0 : completion);
    }

    private Integer readInt(JsonNode parent, String key) {

        JsonNode node = parent.path(key);

        return node.isMissingNode() || node.isNull()
                ? null
                : node.asInt();
    }

    private String safeInt(Integer value) {
        return value == null ? "NA" : value.toString();
    }

    private long elapsedMs(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt
        );
    }

    private void validateConfiguration() {

        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new AICommunicationException(
                    "Ollama base URL is not configured"
            );
        }

        if (!StringUtils.hasText(properties.getChatEndpoint())) {
            throw new AICommunicationException(
                    "Ollama chat endpoint is not configured"
            );
        }

        if (!StringUtils.hasText(properties.getDefaultModel())) {
            throw new AICommunicationException(
                    "Ollama default model is not configured"
            );
        }
    }

    private void validateRequest(AIRequest request) {

        if (request == null) {
            throw new AICommunicationException(
                    "AI request cannot be null"
            );
        }

        if (!StringUtils.hasText(request.getUserPrompt())) {
            throw new AICommunicationException(
                    "AI user prompt cannot be empty"
            );
        }
    }
}