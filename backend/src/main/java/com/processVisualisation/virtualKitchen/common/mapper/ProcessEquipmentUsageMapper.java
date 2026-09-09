package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeEquipmentUsage;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link RecipeEquipmentUsage} entity
 * (a record of equipment used during a recipe execution) and its
 * {@link RecipeEquipmentUsageRequestDTO}/{@link RecipeEquipmentUsageResponseDTO}
 * representations.
 */
@Component
public class ProcessEquipmentUsageMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeEquipmentUsage}
     * entity. The entity's {@code id} is left unset, since it is assigned at
     * persistence time.
     *
     * @param dto the request payload describing the equipment usage to record
     * @return a new, unpersisted {@link RecipeEquipmentUsage} entity populated from {@code dto}
     */
    public RecipeEquipmentUsage toEntity(RecipeEquipmentUsageRequestDTO dto){
        RecipeEquipmentUsage p = new RecipeEquipmentUsage();
        p.setProcessExecutionId(dto.getProcessExecutionId());
        p.setEquipmentId(dto.getEquipmentId());
        p.setUsageDurationSec(dto.getUsageDurationSec());
        p.setCostAtTime(dto.getCostAtTime());
        return p;
    }

    /**
     * Converts a {@link RecipeEquipmentUsage} entity into its response DTO
     * representation for returning to clients.
     *
     * @param p the entity to convert
     * @return a fully populated {@link RecipeEquipmentUsageResponseDTO}
     */
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
