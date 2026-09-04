package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RecipeStepExecutionResponseDTO {

    private Long id;
    private Long processExecutionId;
    private Long stepDefinitionId;
    private RecipeStepStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String notes;
}
