package com.processVisualisation.virtualKitchen.store.dto;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    private String imageUrl;
    private String catalogSlug;
    private String category;
    private String icon;
    private List<String> aliases;
    private List<String> recipeUnits;
    private List<String> preparationStyleSets;
    private Double densityGPerMl;
    private Map<String, Double> unitWeightsG;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}