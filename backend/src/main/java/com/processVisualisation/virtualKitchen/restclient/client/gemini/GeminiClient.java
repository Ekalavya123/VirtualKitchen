package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
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
 * {@link AIClient} implementation that talks to Google's Gemini chat
 * completion API. It is the default text-generation provider for the
 * Virtual Kitchen application, used to produce process-visualization
 * content from prompts. Active when the {@code ai.provider} property is
 * unset or set to {@code gemini}, and is the primary {@link AIClient} bean.
 */
@Component("geminiAiClient")
@Primary
public class GeminiClient implements AIClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);
    private static final int MAX_LOGGED_BODY_CHARS = 500;

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final GeminiPromptCacheService promptCacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Signals that Gemini refused the referenced {@code cachedContent}, so the call is retried with the prompt inline. */
    private static final class CachedContentRejectedException extends RuntimeException {
        private final int status;

        private CachedContentRejectedException(int status) {
            super(null, null, false, false);
            this.status = status;
        }
    }

    /**
     * Creates a client bound to the Gemini REST client and configuration.
     *
     * @param restClient the pre-configured REST client used to call the Gemini API
     * @param properties the configured Gemini API key, endpoints, and default model
     * @param promptCacheService explicit context caches for requests that set {@code cacheSystemPrompt}
     */
    public GeminiClient(
            @Qualifier("geminiRestClient") RestClient restClient,
            GeminiProperties properties,
            GeminiPromptCacheService promptCacheService
    ) {
        this.restClient = restClient;
        this.properties = properties;
        this.promptCacheService = promptCacheService;
    }

    /**
     * Sends a chat request to the Gemini API and returns the parsed
     * response. Validates configuration and the request, builds the Gemini
     * request payload, logs request/response outcomes, and maps HTTP and
     * connectivity failures to the appropriate AI client exception subtype.
     *
     * @param request the prompt, model, and generation parameters to send
     * @return the parsed Gemini response, including content and usage metadata
     * @throws AICommunicationException if Gemini is not configured correctly, the request is invalid, the endpoint URI is invalid, or the API call fails with a non-authentication HTTP error
     * @throws AIAuthenticationException if Gemini rejects the request due to invalid or missing credentials
     * @throws AITimeoutException if the request times out or the connection fails
     * @throws AIInvalidResponseException if Gemini returns an empty response or a response without message content
     */
    @Override
    public AIResponse chat(AIRequest request) {
        validateConfiguration();
        validateRequest(request);

        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel();
        String cacheName = request.isCacheSystemPrompt()
                ? promptCacheService.cacheNameFor(model, request.getSystemPrompt()).orElse(null)
                : null;

        try {
            return execute(request, model, cacheName);
        } catch (CachedContentRejectedException ex) {
            // The cache expired early, was deleted, or is otherwise unusable: forget it and send the prompt inline.
            log.warn("event=ai_prompt_cache_rejected provider=gemini status={} fallback=inline_system_prompt", ex.status);
            promptCacheService.evict(model, request.getSystemPrompt());
            return execute(request, model, null);
        }
    }

    private AIResponse execute(AIRequest request, String model, String cacheName) {

        try {
            String body = objectMapper.writeValueAsString(buildRequestPayload(request, cacheName));

            String rawResponse = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getChatEndpoint())
                            .build(Map.of("model", model)))
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawResponse)) {
                throw new AIInvalidResponseException("Received empty response from Gemini");
            }

            AIResponse parsed = parseResponse(rawResponse, model);
            return parsed;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String responseBody = ex.getResponseBodyAsString();
            if (cacheName != null && (status == 400 || status == 403 || status == 404)) {
                // Most likely the referenced cache, not the request; a genuine error resurfaces on the uncached retry.
                throw new CachedContentRejectedException(status);
            }
            if (status == 401 || status == 403) {
                throw new AIAuthenticationException("Gemini authentication failed", ex);
            }

            if (status == 429) {
                String errorMessage = "Gemini quota exceeded (429 Too Many Requests)";
                try {
                    JsonNode errorNode = objectMapper.readTree(responseBody).path("error");
                    String msg = errorNode.path("message").asText(null);
                    String code = errorNode.path("code").asText(null);
                    if (msg != null) {
                        errorMessage += ": " + msg;
                    }
                    if (code != null) {
                        errorMessage += " code=" + code;
                    }
                } catch (Exception parseEx) {
                    // ignore JSON parse errors and keep generic message
                }
                throw new AICommunicationException(errorMessage, ex);
            }

            log.debug("event=ai_provider_http_error provider=gemini status={} body={}", status, truncate(responseBody));
            throw new AICommunicationException("Gemini API request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AITimeoutException("Gemini request timed out or could not connect", ex);
        } catch (JsonProcessingException ex) {
            throw new AIInvalidResponseException("Failed to process Gemini JSON payload", ex);
        } catch (IllegalArgumentException ex) {
            throw new AICommunicationException("Gemini endpoint configuration is invalid", ex);
        }
    }

    /**
     * @param cacheName a {@code cachedContents/...} name holding the system prompt, or null to send it inline.
     *                  Gemini rejects {@code systemInstruction} alongside {@code cachedContent}, so it's one or the other.
     */
    Map<String, Object> buildRequestPayload(AIRequest request, String cacheName) {
        Map<String, Object> payload = new HashMap<>();
        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", request.getUserPrompt()))
        ));
        payload.put("contents", contents);

        if (cacheName != null) {
            payload.put("cachedContent", cacheName);
        } else if (StringUtils.hasText(request.getSystemPrompt())) {
            payload.put("systemInstruction", Map.of(
                    "parts", List.of(Map.of("text", request.getSystemPrompt()))
            ));
        }

        Map<String, Object> generationConfig = new HashMap<>();
        if (request.getTemperature() != null) {
            generationConfig.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            generationConfig.put("maxOutputTokens", request.getMaxTokens());
        }
        if (request.getResponseSchema() != null) {
            generationConfig.put("responseMimeType", MediaType.APPLICATION_JSON_VALUE);
            generationConfig.put("responseJsonSchema", request.getResponseSchema());
        } else if (StringUtils.hasText(request.getResponseFormat())) {
            generationConfig.put("responseMimeType", mapResponseMimeType(request.getResponseFormat()));
        }

        if (!generationConfig.isEmpty()) {
            payload.put("generationConfig", generationConfig);
        }

        return payload;
    }

    private AIResponse parseResponse(String rawResponse, String requestedModel) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawResponse);

        JsonNode firstCandidate = root.path("candidates").path(0);
        String content = extractText(firstCandidate.path("content"));
        if (!StringUtils.hasText(content)) {
            throw new AIInvalidResponseException("Gemini response does not contain message content");
        }

        JsonNode usage = root.path("usageMetadata");

        return AIResponse.builder()
                .content(content)
                .model(root.path("modelVersion").asText(requestedModel))
                .promptTokens(readInt(usage, "promptTokenCount"))
                .completionTokens(readInt(usage, "candidatesTokenCount"))
                .totalTokens(readInt(usage, "totalTokenCount"))
                .cachedTokens(readInt(usage, "cachedContentTokenCount"))
                .thoughtsTokens(readInt(usage, "thoughtsTokenCount"))
                .finishReason(firstCandidate.path("finishReason").asText(null))
                .rawResponse(rawResponse)
                .build();
    }

    private String extractText(JsonNode contentNode) {
        JsonNode parts = contentNode.path("parts");
        if (!parts.isArray()) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        for (JsonNode part : parts) {
            String text = part.path("text").asText(null);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(text);
        }

        return builder.isEmpty() ? null : builder.toString();
    }

    private String mapResponseMimeType(String responseFormat) {
        String normalized = responseFormat.trim();
        if ("json".equalsIgnoreCase(normalized)
                || "json_object".equalsIgnoreCase(normalized)
                || MediaType.APPLICATION_JSON_VALUE.equalsIgnoreCase(normalized)) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        if ("text".equalsIgnoreCase(normalized)
                || MediaType.TEXT_PLAIN_VALUE.equalsIgnoreCase(normalized)) {
            return MediaType.TEXT_PLAIN_VALUE;
        }
        return normalized;
    }

    private Integer readInt(JsonNode parent, String key) {
        JsonNode node = parent.path(key);
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AICommunicationException("Gemini API key is not configured");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new AICommunicationException("Gemini base URL is not configured");
        }
        if (!StringUtils.hasText(properties.getChatEndpoint())) {
            throw new AICommunicationException("Gemini chat endpoint is not configured");
        }
        if (!StringUtils.hasText(properties.getDefaultModel())) {
            throw new AICommunicationException("Gemini default model is not configured");
        }
    }

    private void validateRequest(AIRequest request) {
        if (request == null) {
            throw new AICommunicationException("AI request cannot be null");
        }
        if (!StringUtils.hasText(request.getUserPrompt())) {
            throw new AICommunicationException("AI user prompt cannot be empty");
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
