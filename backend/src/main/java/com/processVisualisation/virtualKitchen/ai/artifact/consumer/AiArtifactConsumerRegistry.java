package com.processVisualisation.virtualKitchen.ai.artifact.consumer;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves an {@link AiArtifactConsumer} by its persisted {@code consumerId}, so
 * {@code AiArtifactRecoveryJob} can re-drive dependent work for an artifact it knows
 * only as a database row.
 */
@Component
public class AiArtifactConsumerRegistry {

    private final Map<String, AiArtifactConsumer> byId;

    public AiArtifactConsumerRegistry(List<AiArtifactConsumer> consumers) {
        Map<String, AiArtifactConsumer> index = new HashMap<>();
        for (AiArtifactConsumer consumer : consumers) {
            AiArtifactConsumer previous = index.put(consumer.consumerId(), consumer);
            if (previous != null) {
                // Fail at startup rather than run the wrong dependent work during recovery.
                throw new IllegalStateException(
                        "Duplicate AiArtifactConsumer id '" + consumer.consumerId() + "': "
                                + previous.getClass().getName() + " and " + consumer.getClass().getName());
            }
        }
        this.byId = Map.copyOf(index);
    }

    /**
     * @param consumerId the id persisted on the artifact
     * @return the consumer that performs its dependent work, or empty if none is registered —
     *         which happens when an artifact outlives the code that produced it
     */
    public Optional<AiArtifactConsumer> find(String consumerId) {
        return Optional.ofNullable(byId.get(consumerId));
    }
}
