package com.processVisualisation.virtualKitchen.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface ImageGenerationClient {
    public record GeneratedImage(
            String mimeType,
            byte[] data
    ) {}
    GeneratedImage generate(String prompt) throws JsonProcessingException;
}
