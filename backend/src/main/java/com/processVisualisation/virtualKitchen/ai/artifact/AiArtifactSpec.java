package com.processVisualisation.virtualKitchen.ai.artifact;

import com.processVisualisation.virtualKitchen.ai.artifact.codec.AiArtifactCodec;

/**
 * What a caller asks {@code AiRequestQueueService} to persist for a generation, and how.
 * Passing one opts that call site into the artifact store; passing {@code null} leaves
 * behaviour exactly as it was.
 * <p>
 * The codec is held as an <em>instance</em> rather than an id so {@code R} is checked at
 * compile time at the call site — the request path never needs a registry lookup or an
 * unchecked cast.
 *
 * @param artifactKey the reuse key, from {@link AiArtifactKeyBuilder#build}
 * @param codec encodes the result for storage and decodes it on reuse
 * @param consumerId which {@code AiArtifactConsumer} performs the dependent work, so the
 *                   recovery sweeper can re-drive it later
 * @param <R> the AI result type
 */
public record AiArtifactSpec<R>(String artifactKey, AiArtifactCodec<R> codec, String consumerId) {

    public static <R> AiArtifactSpec<R> of(String artifactKey, AiArtifactCodec<R> codec, String consumerId) {
        return new AiArtifactSpec<>(artifactKey, codec, consumerId);
    }
}
