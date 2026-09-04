package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessTemplateStep;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateStepMapper {

    public RecipeProcessTemplateStep toEntity(RecipeProcessTemplateStepRequestDTO dto){
        RecipeProcessTemplateStep step = new RecipeProcessTemplateStep();
        step.setProcessTemplateId(dto.getProcessTemplateId());
        step.setStepDefinitionId(dto.getStepDefinitionId());
        step.setStepOrder(dto.getStepOrder());
        return step;
    }

    public RecipeProcessTemplateStepResponseDTO toDTO(RecipeProcessTemplateStep step){
        return RecipeProcessTemplateStepResponseDTO.builder()
                .id(step.getId())
                .processTemplateId(step.getProcessTemplateId())
                .stepDefinitionId(step.getStepDefinitionId())
                .stepOrder(step.getStepOrder())
                .build();
    }
}

