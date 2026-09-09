package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeVisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeVisualizationStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.VisualizationJobResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeVisualizationService;
import com.processVisualisation.virtualKitchen.ai.service.VisualizationJobService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/recipes")
public class RecipeVisualizationController {

    private final AIRecipeVisualizationService AIRecipeVisualizationService;
    private final VisualizationJobService visualizationJobService;

    public RecipeVisualizationController(
            AIRecipeVisualizationService AIRecipeVisualizationService,
            VisualizationJobService visualizationJobService
    ) {
        this.AIRecipeVisualizationService = AIRecipeVisualizationService;
        this.visualizationJobService = visualizationJobService;
    }

    @PostMapping("/{recipeId}/visualization/generate")
    public ApiResponse<RecipeVisualizationResponseDTO> generate(@PathVariable String recipeId) {
        RecipeVisualizationResponseDTO data = AIRecipeVisualizationService.generateVisualization(recipeId);
        return ApiResponse.<RecipeVisualizationResponseDTO>builder()
                .success(true)
                .message(data.getMessage())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @PostMapping("/{recipeId}/visualization/steps/{stepId}/generate")
    public ApiResponse<RecipeVisualizationStepResponseDTO> generateStep(
            @PathVariable String recipeId,
            @PathVariable String stepId
    ) {
        RecipeVisualizationStepResponseDTO data = AIRecipeVisualizationService.generateVisualizationForStep(recipeId, stepId);
        return ApiResponse.<RecipeVisualizationStepResponseDTO>builder()
                .success(true)
                .message("Visualization generated for step")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Starts an async visualization job for the whole recipe and returns immediately — the
     * actual generation work runs on a background task pool. Poll {@link #getJobStatus} with
     * the returned jobId for progress/completion.
     */
    @PostMapping("/{recipeId}/visualization/jobs")
    public ApiResponse<VisualizationJobResponseDTO> startJob(@PathVariable String recipeId) {
        VisualizationJobResponseDTO data = visualizationJobService.startJob(recipeId);
        return ApiResponse.<VisualizationJobResponseDTO>builder()
                .success(true)
                .message("Visualization job started")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    @GetMapping("/visualization/jobs/{jobId}")
    public ApiResponse<VisualizationJobResponseDTO> getJobStatus(@PathVariable String jobId) {
        VisualizationJobResponseDTO data = visualizationJobService.getJobStatus(jobId);
        return ApiResponse.<VisualizationJobResponseDTO>builder()
                .success(true)
                .message("Visualization job status")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
