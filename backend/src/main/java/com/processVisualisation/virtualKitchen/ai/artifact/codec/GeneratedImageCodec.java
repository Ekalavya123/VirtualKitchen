package com.processVisualisation.virtualKitchen.ai.artifact.codec;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayload;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import org.springframework.stereotype.Component;

/**
 * Stores a generated image's raw bytes, preserving the provider's reported MIME type.
 * <p>
 * This is the codec that closes the original data-loss hole: the bytes it writes are what
 * survive a failed upload to object storage, so the retry uploads the image that was already
 * paid for instead of generating a new one.
 */
@Component
public class GeneratedImageCodec implements AiArtifactCodec<GeneratedImage> {

    /**
     * Versioned because {@code GeneratedImage} is a provider-facing record. If it gains a
     * field, register {@code generated-image-v2} and leave this one decodable so pending
     * artifacts from the previous deployment still recover.
     */
    public static final String ID = "generated-image-v1";

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public AiArtifactPayload encode(GeneratedImage value) {
        String contentType = value.mimeType() != null ? value.mimeType() : DEFAULT_CONTENT_TYPE;
        return AiArtifactPayload.binary(contentType, value.data());
    }

    @Override
    public GeneratedImage decode(AiArtifactPayload payload) {
        return new GeneratedImage(payload.contentType(), payload.asBytes());
    }
}
