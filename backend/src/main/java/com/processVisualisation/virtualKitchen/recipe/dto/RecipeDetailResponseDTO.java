package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload representing the new Recipe Tool's view of a recipe
 * (the existing {@code RecipeTemplate} entity): its base fields plus
 * ingredients, nutrition and the id of its MAIN process. Distinct from
 * {@link RecipeTemplateResponseDTO}, which only carries the base fields for
 * the pre-existing {@code /api/v1/process-templates} endpoints.
 */
@Data
@Builder
public class RecipeDetailResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Long createdBy;
    private Visibility visibility;
    private List<RecipeIngredientDTO> ingredients;
    private NutritionInfoDTO nutrition;
    private Long mainProcessId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
