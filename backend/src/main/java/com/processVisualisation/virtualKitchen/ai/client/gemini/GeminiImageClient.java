package com.processVisualisation.virtualKitchen.ai.client.gemini;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.ai.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.ai.config.GeminiProperties;
import com.processVisualisation.virtualKitchen.ai.exception.AIInvalidResponseException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
public class GeminiImageClient implements ImageGenerationClient {

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiImageClient(
            @Qualifier("geminiRestClient") RestClient restClient,
            GeminiProperties properties,
            ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

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
