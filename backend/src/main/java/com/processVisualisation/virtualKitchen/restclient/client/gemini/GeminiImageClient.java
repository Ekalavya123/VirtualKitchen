package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIAuthenticationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AICommunicationException;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * {@link ImageGenerationClient} implementation that talks to Google's
 * Gemini API to generate images from text prompts for the Virtual Kitchen
 * application's process-visualization feature. Active when the
 * {@code ai.image.provider} property is set to {@code gemini}.
 */
@Component("geminiImageClient")
public class GeminiImageClient implements ImageGenerationClient {

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * Creates a client bound to the Gemini REST client and configuration.
     *
     * @param restClient the pre-configured REST client used to call the Gemini API
     * @param properties the configured Gemini API key, endpoint, and image model
     * @param objectMapper the JSON mapper used to parse Gemini responses
     */
    public GeminiImageClient(
            @Qualifier("geminiRestClient") RestClient restClient,
            GeminiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts and decodes the first inline image found in a raw Gemini
     * generateContent JSON response.
     *
     * @param response the raw JSON response body returned by Gemini
     * @return the decoded generated image
     * @throws JsonProcessingException if the response body is not valid JSON
     * @throws AIInvalidResponseException if the response contains no inline image data
     */
    private GeneratedImage parseImage(String response) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(response);
        for (JsonNode candidate : root.path("candidates")) {
            for (JsonNode part : candidate
                    .path("content")
                    .path("parts")) {

                JsonNode inlineData = part.path("inlineData");
                if (!inlineData.isMissingNode()) {
                    String mimeType = inlineData.path("mimeType").asText();
                    String base64 = inlineData.path("data").asText();
                    return new GeneratedImage(mimeType, Base64.getDecoder().decode(base64)
                    );
                }
            }
        }
        throw new AIInvalidResponseException("Gemini response did not contain an image");
    }

    /**
     * Generates an image for the given prompt by calling the Gemini
     * generateContent endpoint and decoding the returned inline image data.
     * <p>
     * Transport failures are translated into the typed {@code AIClientException}
     * hierarchy, mirroring {@link GeminiClient#chat}. This matters beyond error
     * reporting: {@code AiRequestQueueService} only retries an attempt when it
     * fails with {@link AITimeoutException} or {@link AICommunicationException},
     * so an unwrapped {@code RestClientResponseException} would make
     * {@code ai.request.max-retries} dead configuration for image generation.
     *
     * @param prompt the text prompt describing the image to generate
     * @return the generated image data and its MIME type
     * @throws AIAuthenticationException if Gemini rejects the API key (401/403)
     * @throws AICommunicationException if Gemini returns any other HTTP error, or the endpoint is misconfigured
     * @throws AITimeoutException if the request times out or the host cannot be reached
     * @throws AIInvalidResponseException if the response is not valid JSON or contains no image
     */
    @Override
    public GeneratedImage generate(String prompt) {

        Map<String, Object> request = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts", List.of(
                                        Map.of("text", prompt)
                                )
                        )
                ),
                "generationConfig", Map.of(
                        "responseModalities", List.of("IMAGE")
                )
        );

        try {
            String response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.getChatEndpoint())
                            .build(properties.getImageModel()))
                    .header("x-goog-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseImage(response);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AIAuthenticationException("Gemini image authentication failed", ex);
            }
            throw new AICommunicationException(
                    "Gemini image request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AITimeoutException("Gemini image request timed out or could not connect", ex);
        } catch (JsonProcessingException ex) {
            throw new AIInvalidResponseException("Failed to process Gemini image JSON payload", ex);
        } catch (IllegalArgumentException ex) {
            throw new AICommunicationException("Gemini image endpoint configuration is invalid", ex);
        }
    }
}
