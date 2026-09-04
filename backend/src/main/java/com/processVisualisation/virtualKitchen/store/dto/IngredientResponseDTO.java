package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeUnitType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class IngredientResponseDTO {

    private Long id;
    private String name;
    private String description;
    private RecipeUnitType defaultUnit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
