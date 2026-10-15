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
    /** The recipe's own default preparation guidance; null means the application's default text. */
    private String preparationNotes;
    private Long mainProcessId;
    /** The recipe's current process revision (see {@code RecipeTemplate.processRevision}); 0 if never saved. */
    private Long processRevision;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
