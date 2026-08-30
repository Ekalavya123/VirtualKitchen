package com.processVisualisation.virtualKitchen.ai.client.drawthings;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.ai.config.DrawThingsProperties;
import com.processVisualisation.virtualKitchen.ai.exception.AIInvalidResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "drawthings", matchIfMissing = true)
@Component
public class DrawThingsImageClient implements ImageGenerationClient {

    private final RestClient restClient;
    private final DrawThingsProperties drawThingsProperties;
    private final ObjectMapper objectMapper;

    public DrawThingsImageClient(@Qualifier("drawThingsRestClient") RestClient restClient, DrawThingsProperties drawThingsProperties, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.drawThingsProperties = drawThingsProperties;
        this.objectMapper = objectMapper;
    }

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