package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessStatus;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionResponseDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ProcessExecutionMapper {

    public RecipeProcessExecution toEntity(RecipeProcessExecutionRequestDTO dto){
        RecipeProcessExecution pe = new RecipeProcessExecution();
        pe.setProcessTemplateId(dto.getProcessTemplateId());
        pe.setUserId(dto.getUserId());
        pe.setKitchenId(dto.getKitchenId());
        pe.setStatus(RecipeProcessStatus.NOT_STARTED);
        pe.setStartedAt(LocalDateTime.now());
        return pe;
    }

    public RecipeProcessExecutionResponseDTO toDTO(RecipeProcessExecution pe){
        return RecipeProcessExecutionResponseDTO.builder()
                .id(pe.getId())
                .processTemplateId(pe.getProcessTemplateId())
                .userId(pe.getUserId())
                .kitchenId(pe.getKitchenId())
                .status(pe.getStatus())
                .startedAt(pe.getStartedAt())
                .completedAt(pe.getCompletedAt())
                .generatedMediaUrl(pe.getGeneratedMediaUrl())
                .build();
    }
}

