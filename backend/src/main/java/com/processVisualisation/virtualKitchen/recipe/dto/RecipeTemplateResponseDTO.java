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
}
