package com.processVisualisation.virtualKitchen.ai.platform.registry.dto;

import com.processVisualisation.virtualKitchen.ai.platform.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelTier;
import lombok.Builder;
import lombok.Data;

/** Public view of a registered AI model, for a frontend model picker or support/debugging. */
@Data
@Builder
public class ModelDefinitionResponseDTO {
    private String key;
    private AiCapability capability;
    private ModelTier tier;
    private String providerModelId;
    private int creditCost;
    private boolean enabled;
}
