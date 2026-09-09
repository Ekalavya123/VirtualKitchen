package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepStatus;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link RecipeStepExecution} entity (the
 * execution state of one step within a recipe execution) and its
 * {@link RecipeStepExecutionRequestDTO}/{@link RecipeStepExecutionResponseDTO}
 * representations.
 */
@Component
public class StepExecutionMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeStepExecution}
     * entity. Lossy/derived field: {@code status} is always hardcoded to
     * {@link RecipeStepStatus#NOT_STARTED} rather than read from the DTO.
     * {@code startedAt}, {@code completedAt} and {@code notes} are left
     * unset here — they are only populated later during execution and
     * appear only in the response.
     *
     * @param dto the request payload describing the step execution to create
     * @return a new, unpersisted {@link RecipeStepExecution} entity populated from {@code dto}
     */
    public RecipeStepExecution toEntity(RecipeStepExecutionRequestDTO dto){
        RecipeStepExecution step = new RecipeStepExecution();
        step.setProcessExecutionId(dto.getProcessExecutionId());
        step.setStepDefinitionId(dto.getStepDefinitionId());
        step.setStatus(RecipeStepStatus.NOT_STARTED);
        return step;
    }

    /**
     * Converts a {@link RecipeStepExecution} entity into its response DTO
     * representation for returning to clients.
     *
     * @param step the entity to convert
     * @return a fully populated {@link RecipeStepExecutionResponseDTO}
     */
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
