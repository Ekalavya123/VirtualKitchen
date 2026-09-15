package com.processVisualisation.virtualKitchen.ai.registry;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Read-only, in-memory view over the configured AI model registry
 * ({@link AiModelProperties}). This is the single place model
 * selection/routing consults to answer "which models exist for this
 * capability" and "what does this model fall back to" — recipe and
 * visualization services never reference a provider name or tier directly.
 */
@Component
public class AiModelRegistry {

    private final AiModelProperties properties;
    private final Map<String, ModelDefinition> byKey = new LinkedHashMap<>();
    private final Map<AiCapability, List<ModelDefinition>> byCapability = new EnumMap<>(AiCapability.class);

    public AiModelRegistry(AiModelProperties properties) {
        this.properties = properties;
        for (ModelDefinition model : properties.getModels()) {
            byKey.put(model.getKey(), model);
            byCapability.computeIfAbsent(model.getCapability(), c -> new ArrayList<>()).add(model);
        }
    }

    public Optional<ModelDefinition> find(String key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public List<ModelDefinition> byCapability(AiCapability capability) {
        return byCapability.getOrDefault(capability, List.of());
    }

    /**
     * Resolves the configured default model for a capability.
     *
     * @throws IllegalStateException if no default is configured, the configured
     *         key is unknown, or the configured default model is disabled —
     *         these are all operator configuration errors, not user-facing
     *         "no credits" conditions.
     */
    public ModelDefinition defaultFor(AiCapability capability) {
        String defaultKey = properties.getDefaultModel().get(capability.name());
        if (defaultKey == null) {
            throw new IllegalStateException("No default AI model configured for capability " + capability);
        }
        return find(defaultKey)
                .filter(ModelDefinition::isEnabled)
                .orElseThrow(() -> new IllegalStateException(
                        "Configured default model '" + defaultKey + "' for " + capability + " is missing or disabled"));
    }

    public Optional<ModelDefinition> fallbackFor(ModelDefinition model) {
        if (model.getFallbackModelKey() == null) {
            return Optional.empty();
        }
        return find(model.getFallbackModelKey()).filter(ModelDefinition::isEnabled);
    }
}
