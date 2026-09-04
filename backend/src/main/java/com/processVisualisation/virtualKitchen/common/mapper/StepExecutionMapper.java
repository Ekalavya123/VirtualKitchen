package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepStatus;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class StepExecutionMapper {

    public RecipeStepExecution toEntity(RecipeStepExecutionRequestDTO dto){
        RecipeStepExecution step = new RecipeStepExecution();
        step.setProcessExecutionId(dto.getProcessExecutionId());
        step.setStepDefinitionId(dto.getStepDefinitionId());
        step.setStatus(RecipeStepStatus.NOT_STARTED);
        return step;
    }

    public RecipeStepExecutionResponseDTO toDTO(RecipeStepExecution step){
        return RecipeStepExecutionResponseDTO.builder()
                .id(step.getId())
                .processExecutionId(step.getProcessExecutionId())
                .stepDefinitionId(step.getStepDefinitionId())
                .status(step.getStatus())
                .startedAt(step.getStartedAt())
                .completedAt(step.getCompletedAt())
                .notes(step.getNotes())
                .build();
    }
}

