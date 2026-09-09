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

/**
 * REST controller for triggering AI-driven visualization generation for a recipe process flow,
 * both synchronously (whole recipe or a single step) and as an asynchronous background job that
 * can be polled for progress. Delegates generation to AIRecipeVisualizationService and
 * background job orchestration to VisualizationJobService.
 */
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

    /**
     * Synchronously generates the AI visualization for an entire recipe process flow.
     *
     * @param recipeId the id of the recipe flow to generate a visualization for
     * @return an ApiResponse wrapping the generated visualization data and its own status message
     * @throws com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException if the recipe flow (or a referenced step) cannot be found or the visualization cannot be generated
     */
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

    /**
     * Synchronously generates the AI visualization for a single step within a recipe process
     * flow.
     *
     * @param recipeId the id of the recipe flow containing the step
     * @param stepId   the id of the step to generate a visualization for
     * @return an ApiResponse wrapping the generated step visualization data
     * @throws com.processVisualisation.virtualKitchen.common.exception.RecipeFlowGenerationException if the recipe flow cannot be found or the given stepId is not present in it
     */
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
     * Starts an async visualization job for the whole recipe and returns immediately - the
     * actual generation work runs on a background task pool. Poll {@link #getJobStatus} with
     * the returned jobId for progress/completion.
     *
     * @param recipeId the id of the recipe flow to generate a visualization job for
     * @return an ApiResponse wrapping the newly started job's id and initial status
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

    /**
     * Retrieves the current status (and, once complete, result) of a previously started
     * visualization job.
     *
     * @param jobId the id of the job to look up, as returned by {@link #startJob}
     * @return an ApiResponse wrapping the job's current status/progress/result
     */
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
