package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response payload representing a persisted recipe template, including its
 * ownership, {@link Visibility} setting and creation/update timestamps.
 */
@Data
@Builder
public class RecipeTemplateResponseDTO {

    private Long id;
    private String name;
    private String description;
    private Long createdBy;
    private Visibility visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * The recipe's thumbnail (its latest generated step visual), or null when it has none and
     * {@link #fallbackIcon} should be shown. Populated by the recipe list endpoints only.
     */
    private String thumbnailUrl;

    /** The default recipe icon to show when {@link #thumbnailUrl} is null. Populated with {@link #thumbnailUrl}. */
    private String fallbackIcon;
}
