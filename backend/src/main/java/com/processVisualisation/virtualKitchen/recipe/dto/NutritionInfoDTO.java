package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Request/response payload for a recipe's {@code NutritionInfo}. Every field
 * is optional. Not yet wired to a controller; prepared for a future
 * {@code PUT /recipes/{id}/nutrition}-style endpoint.
 */
@Data
public class NutritionInfoDTO {
    private Double calories;
    private Double proteinGrams;
    private Double carbohydratesGrams;
    private Double fatGrams;
    private Double fiberGrams;
    private Double sodiumMilligrams;
    private Integer servings;
}
