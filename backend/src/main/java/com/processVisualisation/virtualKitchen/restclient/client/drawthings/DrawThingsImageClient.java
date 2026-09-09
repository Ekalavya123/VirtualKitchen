package com.processVisualisation.virtualKitchen.restclient.client.drawthings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.config.DrawThingsProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

/**
 * {@link ImageGenerationClient} implementation that talks to a self-hosted
 * Draw Things image generation server. It is the default image provider for
 * the Virtual Kitchen application, used to render process-visualization
 * images from text prompts. Active when the {@code ai.image.provider}
 * property is unset or set to {@code drawthings}.
 */
@ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "drawthings", matchIfMissing = true)
@Component
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
     *
     * @param prompt the text prompt describing the image to generate
     * @return the generated image data and its MIME type
     * @throws JsonProcessingException if the Draw Things response cannot be parsed as JSON
     */
    @Override
    public GeneratedImage generate(String prompt)
            throws JsonProcessingException {

        Map<String, Object> request = Map.of(
                "prompt", prompt,
                "width", drawThingsProperties.getWidth(),
                "height", drawThingsProperties.getHeight()
        );

        String response = restClient.post()
                .uri(drawThingsProperties.getEndpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);

        return parseImage(response);
    }
}