package com.processVisualisation.virtualKitchen.ai.dto;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
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
