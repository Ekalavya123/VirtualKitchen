package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The caller's background AI jobs for one recipe that the UI should (re)attach to — returned when
 * the recipe tool opens, so a reload or navigation away and back resumes showing progress instead
 * of offering to start the same work again.
 */
@Data
@Builder
public class RecipeActiveJobsResponseDTO {
    /** The running generation job, else a completed one whose result hasn't been applied yet, else null. */
    private RecipeProcessGenerationJobResponseDTO generation;
    /** Every visualization job still QUEUED/IN_PROGRESS for one of this recipe's processes. */
    private List<VisualizationJobResponseDTO> visualizations;
}
