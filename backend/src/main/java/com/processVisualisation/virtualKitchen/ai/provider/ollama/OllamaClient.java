package com.processVisualisation.virtualKitchen.ai.provider.ollama;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.provider.AiClient;
import com.processVisualisation.virtualKitchen.ai.provider.config.OllamaProperties;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiRequest;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiCommunicationException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiInvalidResponseException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiTimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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

/**
 * {@link AiClient} implementation that talks to a locally or self-hosted
 * Ollama server's chat completion API. Used by the Virtual Kitchen
 * application as a text-generation provider when running models locally.
 * Active when the {@code ai.provider} property is set to {@code ollama}.
 */
@Component("ollamaAiClient")
public class OllamaClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);
    private static final int MAX_LOGGED_BODY_CHARS = 500;

    private final RestClient restClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a client bound to the Ollama REST client and configuration.
     *
     * @param restClient the pre-configured REST client used to call the Ollama API
     * @param properties the configured Ollama base URL, chat endpoint, and default model
     */
    public OllamaClient(
            @Qualifier("ollamaRestClient") RestClient restClient,
            OllamaProperties properties) {

        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Sends a chat request to the Ollama API and returns the parsed
     * response. Validates configuration and the request, builds the Ollama
     * request payload, logs request/response outcomes, and maps HTTP and
     * connectivity failures to the appropriate AI client exception subtype.
     *
     * @param request the prompt, model, and generation parameters to send
     * @return the parsed Ollama response, including content and usage metadata
     * @throws AiCommunicationException if Ollama is not configured correctly, the request is invalid, or the API call fails with an HTTP error
     * @throws AiTimeoutException if the request times out or the connection fails
     * @throws AiInvalidResponseException if Ollama returns an empty response, a response without message content, or unparsable JSON
     */
    @Override
    public AiResponse chat(AiRequest request) {

        validateConfiguration();
        validateRequest(request);

        String model = StringUtils.hasText(request.getModel())
                ? request.getModel()
                : properties.getDefaultModel();

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

            if (!StringUtils.hasText(rawResponse)) {

                throw new AiInvalidResponseException(
                        "Received empty response from Ollama"
                );
            }

            AiResponse parsed = parseResponse(rawResponse, model);

            return parsed;

        } catch (RestClientResponseException ex) {

            log.debug("event=ai_provider_http_error provider=ollama status={} body={}",
                    ex.getStatusCode().value(), truncate(ex.getResponseBodyAsString()));

            throw new AiCommunicationException(
                    "Ollama API request failed with status: "
                            + ex.getStatusCode(),
                    ex
            );

        } catch (ResourceAccessException ex) {

            throw new AiTimeoutException(
                    "Ollama request timed out or could not connect",
                    ex
            );

        } catch (JsonProcessingException ex) {

            throw new AiInvalidResponseException(
                    "Failed to process Ollama JSON payload",
                    ex
            );
        }
    }

    private Map<String, Object> buildRequestPayload(
            AiRequest request,
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

        if (properties.getNumCtx() != null) {
            options.put("num_ctx", properties.getNumCtx());
        }

        if (!options.isEmpty()) {
            payload.put("options", options);
        }

        if (StringUtils.hasText(properties.getKeepAlive())) {
            payload.put("keep_alive", properties.getKeepAlive());
        }

        // Constrains decoding to the given JSON Schema, or else to syntactically valid JSON.
        if (request.getResponseSchema() != null) {
            payload.put("format", request.getResponseSchema());
        } else if (isJsonResponseFormat(request.getResponseFormat())) {
            payload.put("format", "json");
        }
        payload.put("think", false);

        return payload;
    }

    private boolean isJsonResponseFormat(String responseFormat) {
        if (!StringUtils.hasText(responseFormat)) return false;
        String normalized = responseFormat.trim();
        return "json".equalsIgnoreCase(normalized)
                || "json_object".equalsIgnoreCase(normalized)
                || MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(normalized);
    }

    private AiResponse parseResponse(
            String rawResponse,
            String requestedModel)
            throws JsonProcessingException {

        JsonNode root = objectMapper.readTree(rawResponse);

        String content = root.path("message")
                .path("content")
                .asText(null);

        if (!StringUtils.hasText(content)) {
            throw new AiInvalidResponseException(
                    "Ollama response does not contain message content"
            );
        }

        return AiResponse.builder()
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
                .providerDurationMs(
                        root.path("total_duration").isNumber() ? root.path("total_duration").asLong() / 1_000_000L : null
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

    private void validateConfiguration() {

        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new AiCommunicationException(
                    "Ollama base URL is not configured"
            );
        }

        if (!StringUtils.hasText(properties.getChatEndpoint())) {
            throw new AiCommunicationException(
                    "Ollama chat endpoint is not configured"
            );
        }

        if (!StringUtils.hasText(properties.getDefaultModel())) {
            throw new AiCommunicationException(
                    "Ollama default model is not configured"
            );
        }
    }

    private void validateRequest(AiRequest request) {

        if (request == null) {
            throw new AiCommunicationException(
                    "AI request cannot be null"
            );
        }

        if (!StringUtils.hasText(request.getUserPrompt())) {
            throw new AiCommunicationException(
                    "AI user prompt cannot be empty"
            );
        }
    }

    /** Provider error bodies can echo request content, so they are only logged at DEBUG and truncated. */
    private static String truncate(String body) {
        if (body == null || body.length() <= MAX_LOGGED_BODY_CHARS) {
            return body;
        }
        return body.substring(0, MAX_LOGGED_BODY_CHARS) + "...[truncated]";
    }
}
