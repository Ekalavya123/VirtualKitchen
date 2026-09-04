package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplateStep;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateStepMapper {

    public RecipeTemplateStep toEntity(RecipeTemplateStepRequestDTO dto){
        RecipeTemplateStep step = new RecipeTemplateStep();
        step.setProcessTemplateId(dto.getProcessTemplateId());
        step.setStepDefinitionId(dto.getStepDefinitionId());
        step.setStepOrder(dto.getStepOrder());
        return step;
    }

    public RecipeTemplateStepResponseDTO toDTO(RecipeTemplateStep step){
        return RecipeTemplateStepResponseDTO.builder()
                .id(step.getId())
                .processTemplateId(step.getProcessTemplateId())
                .stepDefinitionId(step.getStepDefinitionId())
                .stepOrder(step.getStepOrder())
                .build();
    }
}
