package com.processVisualisation.virtualKitchen.ai.artifact.codec;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an {@link AiArtifactCodec} by its persisted {@code codecId}.
 * <p>
 * The request path never needs this: it carries the codec <em>instance</em> in its
 * {@code AiArtifactSpec}, so the payload type is checked at compile time at the call site.
 * The registry exists for {@code AiArtifactRecoveryJob}, which starts from a database row
 * that knows only an id string — making this the single place an unchecked cast is required.
 * <p>
 * Built from a constructor-injected collection, the same way {@code AiClientResolver} is.
 */
@Component
public class AiArtifactCodecRegistry {

    private final Map<String, AiArtifactCodec<?>> byId;

    public AiArtifactCodecRegistry(List<AiArtifactCodec<?>> codecs) {
        Map<String, AiArtifactCodec<?>> index = new HashMap<>();
        for (AiArtifactCodec<?> codec : codecs) {
            AiArtifactCodec<?> previous = index.put(codec.id(), codec);
            if (previous != null) {
                // Fail at startup rather than decode a payload with the wrong codec later.
                throw new IllegalStateException(
                        "Duplicate AiArtifactCodec id '" + codec.id() + "': "
                                + previous.getClass().getName() + " and " + codec.getClass().getName());
            }
        }
        this.byId = Map.copyOf(index);
    }

    /**
     * @param codecId the id persisted on the artifact
     * @return the codec that can decode it, or empty if no such codec is registered —
     *         which happens when an artifact outlives the code that produced it
     */
    public Optional<AiArtifactCodec<?>> find(String codecId) {
        return Optional.ofNullable(byId.get(codecId));
    }
}
