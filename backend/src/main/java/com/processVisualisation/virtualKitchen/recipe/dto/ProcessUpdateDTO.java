package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * Request payload for updating an existing {@code Process} via
 * {@code PUT /api/v1/recipes/{recipeId}/processes/{processId}}: its
 * name/description plus a full replacement of its node/edge graph and
 * viewport. {@code type} is intentionally not editable here — a process's
 * MAIN/SUBPROCESS type is fixed at creation.
 */
@Data
public class ProcessUpdateDTO {

    @NotBlank
    private String name;

    private String description;

    private List<ProcessNodeDTO> nodes;
    private List<ProcessEdgeDTO> edges;
    private ProcessViewportDTO viewport;
}
