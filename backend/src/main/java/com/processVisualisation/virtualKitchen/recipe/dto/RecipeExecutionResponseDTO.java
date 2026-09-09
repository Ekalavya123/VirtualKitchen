package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response payload representing the current state of a recipe execution, including
 * its lifecycle {@link RecipeStatus}, timing information and any generated media
 * produced while the recipe was run.
 */
@Data
@Builder
public class RecipeExecutionResponseDTO {

    private Long id;
    private Long processTemplateId;
    private Long userId;
    private Long kitchenId;
    private RecipeStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String generatedMediaUrl;
}
