package com.processVisualisation.virtualKitchen.ai.artifact.codec;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactPayload;

/**
 * Converts one AI result type to and from a storable {@code AiArtifact} payload.
 * <p>
 * The codec — not the artifact service — decides whether its type is binary or text,
 * because only it knows the shape of what it is encoding.
 * <p>
 * {@link #id()} is persisted on every artifact, which is what lets the recovery sweeper
 * decode a payload it did not produce and knows nothing about at compile time. Treat the id
 * as a versioned contract: if the encoded shape ever changes incompatibly, register a new id
 * and keep the old codec decodable, so artifacts written by the previous deployment still
 * recover after a rolling update.
 *
 * @param <R> the AI result type this codec handles
 */
public interface AiArtifactCodec<R> {

    /** Stable, versioned identifier persisted as {@code AiArtifact.codecId}, e.g. {@code "generated-image-v1"}. */
    String id();

    /**
     * Encodes a freshly generated AI result for storage.
     *
     * @param value the provider's result
     * @return the encoded payload
     * @throws Exception if the value cannot be encoded
     */
    AiArtifactPayload encode(R value) throws Exception;

    /**
     * Decodes a stored payload back into the AI result type.
     *
     * @param payload the payload as stored; may have been promoted from inline to binary,
     *                so prefer {@link AiArtifactPayload#asBytes()}/{@link AiArtifactPayload#asText()}
     *                over reading the raw components
     * @return the reconstructed result
     * @throws Exception if the payload cannot be decoded
     */
    R decode(AiArtifactPayload payload) throws Exception;
}
