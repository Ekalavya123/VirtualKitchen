package com.processVisualisation.virtualKitchen.common.mapper;

import com.processVisualisation.virtualKitchen.store.dto.IngredientRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientResponseDTO;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import org.springframework.stereotype.Component;

@Component
public class IngredientMapper {

    public Ingredient toEntity(IngredientRequestDTO dto) {
        Ingredient ingredient = new Ingredient();
        ingredient.setName(dto.getName());
        ingredient.setDescription(dto.getDescription());
        ingredient.setDefaultUnit(dto.getDefaultUnit());
        return ingredient;
    }

    public IngredientResponseDTO toDTO(Ingredient ingredient) {
        return IngredientResponseDTO.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .description(ingredient.getDescription())
                .defaultUnit(ingredient.getDefaultUnit())
                .createdAt(ingredient.getCreatedAt())
                .updatedAt(ingredient.getUpdatedAt())
                .build();
    }
}