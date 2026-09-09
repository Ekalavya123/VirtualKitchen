package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VisualizationJobResponseDTO {
    private String jobId;
    private String recipeId;
    private String status;
    private int totalSteps;
    private int completedSteps;
    private List<StepResultDTO> steps;

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
