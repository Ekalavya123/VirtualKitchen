package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessTemplateStep;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateStepResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessTemplateStepMapper {

    public ProcessTemplateStep toEntity(ProcessTemplateStepRequestDTO dto){
        ProcessTemplateStep step = new ProcessTemplateStep();
        step.setProcessTemplateId(dto.getProcessTemplateId());
        step.setStepDefinitionId(dto.getStepDefinitionId());
        step.setStepOrder(dto.getStepOrder());
        return step;
    }

    public ProcessTemplateStepResponseDTO toDTO(ProcessTemplateStep step){
        return ProcessTemplateStepResponseDTO.builder()
                .id(step.getId())
                .processTemplateId(step.getProcessTemplateId())
                .stepDefinitionId(step.getStepDefinitionId())
                .stepOrder(step.getStepOrder())
                .build();
    }
}
