package com.processVisualisation.virtualKitchen.restclient.client.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.config.OpenAIProperties;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
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
 * {@link AIClient} implementation that talks to OpenAI's chat completion
 * API. Used by the Virtual Kitchen application as a text-generation
 * provider. Registered under the bean name {@code openAiAiClient} so it can
 * be selected per-request by the AI model routing layer alongside every
 * other provider bean.
 */
@Component("openAiAiClient")
public class OpenAIClient implements AIClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAIClient.class);
    private static final int MAX_LOGGED_BODY_CHARS = 500;

    private final RestClient restClient;
    private final OpenAIProperties properties;
        private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Creates a client bound to the OpenAI REST client and configuration.
     *
     * @param restClient the pre-configured REST client used to call the OpenAI API
     * @param properties the configured OpenAI API key, endpoints, and default model
     */
    public OpenAIClient(
            @Qualifier("openAiRestClient") RestClient restClient,
            OpenAIProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * Sends a chat request to the OpenAI API and returns the parsed
     * response. Validates configuration and the request, builds the OpenAI
     * request payload, logs request/response outcomes, and maps HTTP and
     * connectivity failures to the appropriate AI client exception subtype.
     *
     * @param request the prompt, model, and generation parameters to send
     * @return the parsed OpenAI response, including content and usage metadata
     * @throws AICommunicationException if OpenAI is not configured correctly, the request is invalid, or the API call fails with a non-authentication HTTP error
     * @throws AIAuthenticationException if OpenAI rejects the request due to invalid or missing credentials
     * @throws AITimeoutException if the request times out or the connection fails
     * @throws AIInvalidResponseException if OpenAI returns an empty response, a response without message content, or unparsable JSON
     */
    @Override
    public AIResponse chat(AIRequest request) {
        validateConfiguration();
        validateRequest(request);

        String model = StringUtils.hasText(request.getModel()) ? request.getModel() : properties.getDefaultModel();

        try {
            String body = objectMapper.writeValueAsString(buildRequestPayload(request, model));

            String rawResponse = restClient.post()
                    .uri(properties.getChatEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            if (!StringUtils.hasText(rawResponse)) {
                throw new AIInvalidResponseException("Received empty response from OpenAI");
            }

            AIResponse parsed = parseResponse(rawResponse);
            return parsed;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            String responseBody = ex.getResponseBodyAsString();
            if (status == 401 || status == 403) {
                throw new AIAuthenticationException("OpenAI authentication failed", ex);
            }

            if (status == 429) {
                String errorMessage = "OpenAI quota exceeded (429 Too Many Requests)";
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

            log.debug("event=ai_provider_http_error provider=openai status={} body={}", status, truncate(responseBody));
            throw new AICommunicationException("OpenAI API request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AITimeoutException("OpenAI request timed out or could not connect", ex);
        } catch (JsonProcessingException ex) {
            throw new AIInvalidResponseException("Failed to process OpenAI JSON payload", ex);
        }
    }

    private Map<String, Object> buildRequestPayload(AIRequest request, String model) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);

        List<Map<String, String>> messages = new ArrayList<>();
        if (StringUtils.hasText(request.getSystemPrompt())) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", request.getUserPrompt()));
        payload.put("messages", messages);

        if (request.getTemperature() != null) {
            payload.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            payload.put("max_tokens", request.getMaxTokens());
        }
        if (request.getResponseSchema() != null) {
            // Non-strict: strict mode would force every property to be present, defeating omit-when-empty schemas.
            payload.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", StringUtils.hasText(request.getResponseSchemaName()) ? request.getResponseSchemaName() : "response",
                            "schema", request.getResponseSchema(),
                            "strict", false
                    )
            ));
        } else if (StringUtils.hasText(request.getResponseFormat())) {
            payload.put("response_format", Map.of("type", request.getResponseFormat()));
        }
        return payload;
    }

    private AIResponse parseResponse(String rawResponse) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(rawResponse);

        JsonNode firstChoice = root.path("choices").path(0);
        String content = firstChoice.path("message").path("content").asText(null);
        if (!StringUtils.hasText(content)) {
            throw new AIInvalidResponseException("OpenAI response does not contain message content");
        }

        JsonNode usage = root.path("usage");

        return AIResponse.builder()
                .content(content)
                .model(root.path("model").asText(properties.getDefaultModel()))
                .promptTokens(readInt(usage, "prompt_tokens"))
                .completionTokens(readInt(usage, "completion_tokens"))
                .totalTokens(readInt(usage, "total_tokens"))
                .finishReason(firstChoice.path("finish_reason").asText(null))
                .rawResponse(rawResponse)
                .build();
    }

    private Integer readInt(JsonNode parent, String key) {
        JsonNode node = parent.path(key);
        return node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new AICommunicationException("OpenAI API key is not configured");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new AICommunicationException("OpenAI base URL is not configured");
        }
        if (!StringUtils.hasText(properties.getChatEndpoint())) {
            throw new AICommunicationException("OpenAI chat endpoint is not configured");
        }
        if (!StringUtils.hasText(properties.getDefaultModel())) {
            throw new AICommunicationException("OpenAI default model is not configured");
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
