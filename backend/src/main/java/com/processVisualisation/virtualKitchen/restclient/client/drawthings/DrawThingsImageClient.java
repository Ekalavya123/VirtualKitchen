package com.processVisualisation.virtualKitchen.restclient.client.drawthings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.config.DrawThingsProperties;
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
import java.util.Map;

/**
 * {@link ImageGenerationClient} implementation that talks to a self-hosted
 * Draw Things image generation server. It is the default image provider for
 * the Virtual Kitchen application, used to render process-visualization
 * images from text prompts. Registered under the bean name
 * {@code drawThingsImageClient} so it can be selected per-request by the AI
 * model routing layer alongside every other provider bean.
 */
@Component("drawThingsImageClient")
public class DrawThingsImageClient implements ImageGenerationClient {

    private final RestClient restClient;
    private final DrawThingsProperties drawThingsProperties;
    private final ObjectMapper objectMapper;

    /**
     * Creates a client bound to the Draw Things REST client and configuration.
     *
     * @param restClient the pre-configured REST client used to call the Draw Things endpoint
     * @param drawThingsProperties the configured Draw Things endpoint, dimensions, and timeout
     * @param objectMapper the JSON mapper used to parse Draw Things responses
     */
    public DrawThingsImageClient(@Qualifier("drawThingsRestClient") RestClient restClient, DrawThingsProperties drawThingsProperties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.drawThingsProperties = drawThingsProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * Extracts and decodes the first generated image from a raw Draw Things
     * JSON response.
     *
     * @param response the raw JSON response body returned by Draw Things
     * @return the decoded generated image
     * @throws JsonProcessingException if the response body is not valid JSON
     * @throws AIInvalidResponseException if the response contains no image or an invalid/empty base64 payload
     */
    private GeneratedImage parseImage(String response) throws JsonProcessingException {

        JsonNode root = objectMapper.readTree(response);

        JsonNode images = root.path("images");

        if (!images.isArray() || images.isEmpty()) {
            throw new AIInvalidResponseException(
                    "Draw Things response did not contain an image"
            );
        }

        String base64 = images.get(0).asText();

        if (base64 == null || base64.isBlank()) {
            throw new AIInvalidResponseException(
                    "Draw Things returned an empty image"
            );
        }

        // Handle data:image/png;base64,... if returned
        if (base64.startsWith("data:")) {
            int commaIndex = base64.indexOf(',');

            if (commaIndex > 0) {
                base64 = base64.substring(commaIndex + 1);
            }
        }

        try {
            byte[] imageBytes =
                    Base64.getDecoder().decode(base64);

            return new GeneratedImage(
                    "image/png",
                    imageBytes
            );

        } catch (IllegalArgumentException e) {
            throw new AIInvalidResponseException(
                    "Invalid base64 image returned by Draw Things"
            );
        }
    }

    /**
     * Generates an image for the given prompt by calling the configured
     * Draw Things endpoint and decoding the returned base64 image.
     * <p>
     * Transport failures are translated into the typed {@code AIClientException}
     * hierarchy. A self-hosted Draw Things server is the most likely provider to
     * be transiently unreachable, and {@code AiRequestQueueService} only retries
     * an attempt that fails with {@link AITimeoutException} or
     * {@link AICommunicationException} — so without this wrapping a restarting
     * server would fail the step outright instead of being retried.
     *
     * @param prompt the text prompt describing the image to generate
     * @return the generated image data and its MIME type
     * @throws AIAuthenticationException if the endpoint rejects the request (401/403)
     * @throws AICommunicationException if the endpoint returns any other HTTP error, or is misconfigured
     * @throws AITimeoutException if the request times out or the host cannot be reached
     * @throws AIInvalidResponseException if the response is not valid JSON or contains no usable image
     */
    @Override
    public GeneratedImage generate(String prompt) {

        Map<String, Object> request = Map.of(
                "prompt", prompt,
                "width", drawThingsProperties.getWidth(),
                "height", drawThingsProperties.getHeight()
        );

        try {
            String response = restClient.post()
                    .uri(drawThingsProperties.getEndpoint())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);

            return parseImage(response);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new AIAuthenticationException("Draw Things authentication failed", ex);
            }
            throw new AICommunicationException(
                    "Draw Things request failed with status: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new AITimeoutException("Draw Things request timed out or could not connect", ex);
        } catch (JsonProcessingException ex) {
            throw new AIInvalidResponseException("Failed to process Draw Things JSON payload", ex);
        } catch (IllegalArgumentException ex) {
            throw new AICommunicationException("Draw Things endpoint configuration is invalid", ex);
        }
    }
}