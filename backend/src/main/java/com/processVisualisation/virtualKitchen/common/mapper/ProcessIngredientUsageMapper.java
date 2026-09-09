package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredientUsage;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;
import org.springframework.stereotype.Component;

/**
 * Centralizes conversion between the {@link RecipeIngredientUsage} entity
 * (a record of an ingredient consumed during a recipe execution) and its
 * {@link RecipeIngredientUsageRequestDTO}/{@link RecipeIngredientUsageResponseDTO}
 * representations.
 */
@Component
public class ProcessIngredientUsageMapper {

    /**
     * Converts an incoming request DTO into a new {@link RecipeIngredientUsage}
     * entity. The entity's {@code id} is left unset, since it is assigned at
     * persistence time.
     *
     * @param dto the request payload describing the ingredient usage to record
     * @return a new, unpersisted {@link RecipeIngredientUsage} entity populated from {@code dto}
     */
    public RecipeIngredientUsage toEntity(RecipeIngredientUsageRequestDTO dto){
        RecipeIngredientUsage p = new RecipeIngredientUsage();
        p.setProcessExecutionId(dto.getProcessExecutionId());
        p.setIngredientId(dto.getIngredientId());
        p.setQuantityUsed(dto.getQuantityUsed());
        p.setUnit(dto.getUnit());
        p.setCostAtTime(dto.getCostAtTime());
        return p;
    }

    /**
     * Converts a {@link RecipeIngredientUsage} entity into its response DTO
     * representation for returning to clients.
     *
     * @param p the entity to convert
     * @return a fully populated {@link RecipeIngredientUsageResponseDTO}
     */
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
