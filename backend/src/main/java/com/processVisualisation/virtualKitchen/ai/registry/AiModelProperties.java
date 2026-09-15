package com.processVisualisation.virtualKitchen.ai.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binds the {@code ai.models[*]} list and {@code ai.default-model.*} map from
 * configuration. Kept as a thin binding target only — {@link AiModelRegistry}
 * is the component the rest of the application actually queries.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {

    private List<ModelDefinition> models = new ArrayList<>();

    /** Default model key per capability, keyed by {@link AiCapability#name()}. */
    private Map<String, String> defaultModel = new LinkedHashMap<>();
}
