package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload for creating a new {@code Process} under a recipe, via
 * {@code POST /api/v1/recipes/{recipeId}/processes}. The owning recipe is
 * taken from the URL path, not this payload. Node/edge graph content is
 * submitted separately via update (mirroring how the legacy flow editor
 * saves nodes/edges independently of recipe creation).
 */
@Data
public class ProcessRequestDTO {

    @NotNull
    private ProcessType type;

    @NotBlank
    private String name;

    private String description;
}
