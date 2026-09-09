package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepDefinition;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link RecipeStepDefinition} entity
 * (a reusable, named cooking step) and its
 * {@link RecipeStepDefinitionRequestDTO}/{@link RecipeStepDefinitionResponseDTO}
 * representations.
 */
@Component
public class StepDefinitionMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeStepDefinition}
     * entity. The entity's {@code id} is left unset, since it is assigned at
     * persistence time.
     *
     * @param dto the request payload describing the step definition to create
     * @return a new, unpersisted {@link RecipeStepDefinition} entity populated from {@code dto}
     */
    public RecipeStepDefinition toEntity(RecipeStepDefinitionRequestDTO dto){
        RecipeStepDefinition step = new RecipeStepDefinition();
        step.setName(dto.getName());
        step.setDescription(dto.getDescription());
        step.setMediaUrl(dto.getMediaUrl());
        step.setEstimatedTimeSec(dto.getEstimatedTimeSec());
        return step;
    }

    /**
     * Converts a {@link RecipeStepDefinition} entity into its response DTO
     * representation for returning to clients.
     *
     * @param step the entity to convert
     * @return a fully populated {@link RecipeStepDefinitionResponseDTO}
     */
    public RecipeStepDefinitionResponseDTO toDTO(RecipeStepDefinition step){
        return RecipeStepDefinitionResponseDTO.builder()
                .id(step.getId())
                .name(step.getName())
                .description(step.getDescription())
                .mediaUrl(step.getMediaUrl())
                .estimatedTimeSec(step.getEstimatedTimeSec())
                .build();
    }
}
