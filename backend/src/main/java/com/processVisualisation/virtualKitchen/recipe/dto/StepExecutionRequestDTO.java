package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class StepExecutionRequestDTO {

    private Long processExecutionId;
    private Long stepDefinitionId;
}
