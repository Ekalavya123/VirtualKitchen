package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response payload reporting the progress and results of an asynchronous,
 * AI-driven visualization job that generates images for a recipe's steps. Exposes
 * overall job status/progress plus the per-step outcomes for step-wise polling.
 */
@Data
@Builder
public class VisualizationJobResponseDTO {
    private String jobId;
    private String recipeId;
    private String status;
    private int totalSteps;
    private int completedSteps;
    private List<StepResultDTO> steps;

    /**
     * Outcome of generating a visualization image for a single recipe step within
     * a {@link VisualizationJobResponseDTO}, including success/failure details.
     */
    @Data
    @Builder
    public static class StepResultDTO {
        private String stepId;
        private boolean success;
        private Long visualizationAssetId;
        private String imageUrl;
        private String errorMessage;
    }
}
