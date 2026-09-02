package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.StepDefinition;
import com.processVisualisation.virtualKitchen.recipe.dto.StepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.StepDefinitionResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class StepDefinitionMapper {

    public StepDefinition toEntity(StepDefinitionRequestDTO dto){
        StepDefinition step = new StepDefinition();
        step.setName(dto.getName());
        step.setDescription(dto.getDescription());
        step.setMediaUrl(dto.getMediaUrl());
        step.setEstimatedTimeSec(dto.getEstimatedTimeSec());
        return step;
    }

    public StepDefinitionResponseDTO toDTO(StepDefinition step){
        return StepDefinitionResponseDTO.builder()
                .id(step.getId())
                .name(step.getName())
                .description(step.getDescription())
                .mediaUrl(step.getMediaUrl())
                .estimatedTimeSec(step.getEstimatedTimeSec())
                .build();
    }
}
