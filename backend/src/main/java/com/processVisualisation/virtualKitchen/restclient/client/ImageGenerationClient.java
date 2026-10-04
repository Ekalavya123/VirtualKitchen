package com.processVisualisation.virtualKitchen.restclient.client;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Common contract implemented by every image-generation AI provider client
 * (Draw Things, Gemini) used by the Virtual Kitchen application to turn a
 * text prompt into a generated image.
 */
public interface ImageGenerationClient {

    /**
     * Holds the bytes and MIME type of an image produced by an image
     * generation provider.
     *
     * @param mimeType the MIME type of the generated image (e.g. "image/png")
     * @param data the raw image bytes
     * @param usage the tokens the provider reported for this call ({@link ProviderUsage#NONE} if none)
     */
    public record GeneratedImage(
            String mimeType,
            byte[] data,
            ProviderUsage usage
    ) {
        /** An image whose provider reports no token usage (or one restored from storage, where usage is moot). */
        public GeneratedImage(String mimeType, byte[] data) {
            this(mimeType, data, ProviderUsage.NONE);
        }
    }

    /**
     * Generates an image from the given text prompt using the underlying
     * provider.
     *
     * @param prompt the text prompt describing the image to generate
     * @return the generated image data and its MIME type
     * @throws JsonProcessingException if the provider's response cannot be parsed as JSON
     */
    GeneratedImage generate(String prompt) throws JsonProcessingException;
}
