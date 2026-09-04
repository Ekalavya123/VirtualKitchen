package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeEquipmentUsage;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ProcessEquipmentUsageMapper {

    public RecipeEquipmentUsage toEntity(RecipeEquipmentUsageRequestDTO dto){
        RecipeEquipmentUsage p = new RecipeEquipmentUsage();
        p.setProcessExecutionId(dto.getProcessExecutionId());
        p.setEquipmentId(dto.getEquipmentId());
        p.setUsageDurationSec(dto.getUsageDurationSec());
        p.setCostAtTime(dto.getCostAtTime());
        return p;
    }

    public RecipeEquipmentUsageResponseDTO toDTO(RecipeEquipmentUsage p){
        return RecipeEquipmentUsageResponseDTO.builder()
                .id(p.getId())
                .processExecutionId(p.getProcessExecutionId())
                .equipmentId(p.getEquipmentId())
                .usageDurationSec(p.getUsageDurationSec())
                .costAtTime(p.getCostAtTime())
                .build();
    }
}
