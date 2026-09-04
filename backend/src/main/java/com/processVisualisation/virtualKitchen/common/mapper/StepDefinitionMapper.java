package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepDefinition;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class StepDefinitionMapper {

    public RecipeStepDefinition toEntity(RecipeStepDefinitionRequestDTO dto){
        RecipeStepDefinition step = new RecipeStepDefinition();
        step.setName(dto.getName());
        step.setDescription(dto.getDescription());
        step.setMediaUrl(dto.getMediaUrl());
        step.setEstimatedTimeSec(dto.getEstimatedTimeSec());
        return step;
    }

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
