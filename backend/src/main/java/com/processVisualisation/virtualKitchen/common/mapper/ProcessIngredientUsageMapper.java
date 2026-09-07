package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredientUsage;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessIngredientUsageMapper {

    public RecipeIngredientUsage toEntity(RecipeIngredientUsageRequestDTO dto){
        RecipeIngredientUsage p = new RecipeIngredientUsage();
        p.setProcessExecutionId(dto.getProcessExecutionId());
        p.setIngredientId(dto.getIngredientId());
        p.setQuantityUsed(dto.getQuantityUsed());
        p.setUnit(dto.getUnit());
        p.setCostAtTime(dto.getCostAtTime());
        return p;
    }

    public RecipeIngredientUsageResponseDTO toDTO(RecipeIngredientUsage p){
        return RecipeIngredientUsageResponseDTO.builder()
                .id(p.getId())
                .processExecutionId(p.getProcessExecutionId())
                .ingredientId(p.getIngredientId())
                .quantityUsed(p.getQuantityUsed())
                .unit(p.getUnit())
                .costAtTime(p.getCostAtTime())
                .build();
    }
}
