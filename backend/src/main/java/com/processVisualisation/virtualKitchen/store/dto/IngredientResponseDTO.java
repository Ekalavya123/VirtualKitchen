package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response payload representing an ingredient catalog entry, including its
 * identifier, default {@link UnitType}, and creation/update timestamps.
 */
@Data
@Builder
public class IngredientResponseDTO {

    private Long id;
    private String name;
    private String description;
    private UnitType defaultUnit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}