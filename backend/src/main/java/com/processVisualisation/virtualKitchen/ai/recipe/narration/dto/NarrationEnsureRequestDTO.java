package com.processVisualisation.virtualKitchen.ai.recipe.narration.dto;

import lombok.Data;

/** Optional body for ensuring one step's narration. */
@Data
public class NarrationEnsureRequestDTO {
    /** Regenerate even if current narration is READY or recently FAILED. Recipe owner only. */
    private boolean force;
}
