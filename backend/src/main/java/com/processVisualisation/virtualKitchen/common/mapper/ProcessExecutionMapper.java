package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeExecution;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeStatus;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ProcessExecutionMapper {

    public RecipeExecution toEntity(RecipeExecutionRequestDTO dto){
        RecipeExecution pe = new RecipeExecution();
        pe.setProcessTemplateId(dto.getProcessTemplateId());
        pe.setUserId(dto.getUserId());
        pe.setKitchenId(dto.getKitchenId());
        pe.setStatus(RecipeStatus.NOT_STARTED);
        pe.setStartedAt(LocalDateTime.now());
        return pe;
    }

    public RecipeExecutionResponseDTO toDTO(RecipeExecution pe){
        return RecipeExecutionResponseDTO.builder()
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
