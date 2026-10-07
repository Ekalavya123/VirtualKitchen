package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * The image that represents a recipe, as resolved by the backend: its most recently generated
 * step visual, or the default recipe icon when it has none.
 */
@Data
@Builder
public class RecipeThumbnailDTO {

    public enum Source {
        /** {@link #thumbnailUrl} is the recipe's most recently generated step visual. */
        GENERATED_VISUAL,
        /** The recipe has no usable generated visual; show {@link #fallbackIcon}. */
        DEFAULT
    }

    private Long recipeId;
    /** The image to show, or null when {@link #source} is DEFAULT. */
    private String thumbnailUrl;
    private Source source;
    /** The default recipe icon (an emoji), to show when there is no {@link #thumbnailUrl}. */
    private String fallbackIcon;
    /** The visualization asset the thumbnail came from (GENERATED_VISUAL only). */
    private Long visualizationAssetId;
    /** The process step the thumbnail visual belongs to (GENERATED_VISUAL only). */
    private String stepId;
    /** When the thumbnail visual was generated (GENERATED_VISUAL only). */
    private LocalDateTime generatedAt;
}
