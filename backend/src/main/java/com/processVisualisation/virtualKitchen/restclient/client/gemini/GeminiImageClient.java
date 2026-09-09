package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.restclient.exception.AIInvalidResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * {@link ImageGenerationClient} implementation that talks to Google's
 * Gemini API to generate images from text prompts for the Virtual Kitchen
 * application's process-visualization feature. Active when the
 * {@code ai.image.provider} property is set to {@code gemini}.
 */
@ConditionalOnProperty(prefix = "ai.image", name = "provider", havingValue = "gemini", matchIfMissing = false)
@Component
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
     *
     * @param prompt the text prompt describing the image to generate
     * @return the generated image data and its MIME type
     * @throws JsonProcessingException if the Gemini response cannot be parsed as JSON
     */
    @Override
    public GeneratedImage generate(String prompt) throws JsonProcessingException {

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
    }
}
