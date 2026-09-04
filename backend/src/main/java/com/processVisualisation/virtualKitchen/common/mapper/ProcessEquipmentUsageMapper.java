package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeProcessEquipmentUsage;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessEquipmentUsageMapper {

    public RecipeProcessEquipmentUsage toEntity(RecipeProcessEquipmentUsageRequestDTO dto){
        RecipeProcessEquipmentUsage p = new RecipeProcessEquipmentUsage();
        p.setProcessExecutionId(dto.getProcessExecutionId());
        p.setEquipmentId(dto.getEquipmentId());
        p.setUsageDurationSec(dto.getUsageDurationSec());
        p.setCostAtTime(dto.getCostAtTime());
        return p;
    }

    public RecipeProcessEquipmentUsageResponseDTO toDTO(RecipeProcessEquipmentUsage p){
        return RecipeProcessEquipmentUsageResponseDTO.builder()
                .id(p.getId())
                .processExecutionId(p.getProcessExecutionId())
                .equipmentId(p.getEquipmentId())
                .usageDurationSec(p.getUsageDurationSec())
                .costAtTime(p.getCostAtTime())
                .build();
    }
}

