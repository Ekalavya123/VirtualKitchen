package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplateStep;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link RecipeTemplateStep} entity (a
 * step's position within a recipe template) and its
 * {@link RecipeTemplateStepRequestDTO}/{@link RecipeTemplateStepResponseDTO}
 * representations.
 */
@Component
public class ProcessTemplateStepMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeTemplateStep}
     * entity. The entity's {@code id} is left unset, since it is assigned at
     * persistence time.
     *
     * @param dto the request payload describing the template step to create
     * @return a new, unpersisted {@link RecipeTemplateStep} entity populated from {@code dto}
     */
    public RecipeTemplateStep toEntity(RecipeTemplateStepRequestDTO dto){
        RecipeTemplateStep step = new RecipeTemplateStep();
        step.setProcessTemplateId(dto.getProcessTemplateId());
        step.setStepDefinitionId(dto.getStepDefinitionId());
        step.setStepOrder(dto.getStepOrder());
        return step;
    }

    /**
     * Converts a {@link RecipeTemplateStep} entity into its response DTO
     * representation for returning to clients.
     *
     * @param step the entity to convert
     * @return a fully populated {@link RecipeTemplateStepResponseDTO}
     */
    public RecipeTemplateStepResponseDTO toDTO(RecipeTemplateStep step){
        return RecipeTemplateStepResponseDTO.builder()
                .id(step.getId())
                .processTemplateId(step.getProcessTemplateId())
                .stepDefinitionId(step.getStepDefinitionId())
                .stepOrder(step.getStepOrder())
                .build();
    }
}
