package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeGenerationService;
import com.processVisualisation.virtualKitchen.ai.service.RecipeFlowGenerationJobService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST controller exposing the AI-driven recipe flow generation endpoint under
 * {@code /api/recipe}. Delegates the actual flow generation logic to
 * {@link AIRecipeGenerationService}.
 */
@RestController
@RequestMapping("/api/recipe")
public class AIRecipeGenerationController {

    private final AIRecipeGenerationService AIRecipeGenerationService;
    private final RecipeFlowGenerationJobService recipeFlowGenerationJobService;

    public AIRecipeGenerationController(
            AIRecipeGenerationService AIRecipeGenerationService,
            RecipeFlowGenerationJobService recipeFlowGenerationJobService
    ) {
        this.AIRecipeGenerationService = AIRecipeGenerationService;
        this.recipeFlowGenerationJobService = recipeFlowGenerationJobService;
    }

    /**
     * Generates a structured recipe flow (steps/process visualization) from a
     * free-form recipe description using AI. The AI model used (premium or a
     * standard fallback) is resolved per-user based on remaining AI credits;
     * see the response's {@code usedFallback}/{@code fallbackReason} fields.
     *
     * @param request the validated request containing the raw recipe text
     * @return the generated recipe flow response
     */
    @PostMapping("/generate-flow")
    public RecipeFlowGenerationResponseDTO generateFlow(@Valid @RequestBody RecipeFlowGenerationRequestDTO request) {
        Long userId = currentUserId();
        return AIRecipeGenerationService.generateFlow(userId, request.getRecipe(), request.getClientRequestId());
    }

    /**
     * Starts an async recipe-flow generation job and returns immediately - the actual AI call
     * runs on a background task pool. Poll {@link #getJobStatus} with the returned jobId for
     * progress/completion, same pattern as the visualization job endpoints.
     *
     * @param request the validated request containing the raw recipe text
     * @return an ApiResponse wrapping the newly started job's id and initial status
     */
    @PostMapping("/generate-flow/jobs")
    public ApiResponse<RecipeFlowGenerationJobResponseDTO> startJob(@Valid @RequestBody RecipeFlowGenerationRequestDTO request) {
        Long userId = currentUserId();
        RecipeFlowGenerationJobResponseDTO data = recipeFlowGenerationJobService.startJob(
                userId, request.getRecipe(), request.getClientRequestId());
        return ApiResponse.<RecipeFlowGenerationJobResponseDTO>builder()
                .success(true)
                .message("Recipe flow generation job started")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Retrieves the current status (and, once complete, result) of a previously started recipe
     * flow generation job.
     *
     * @param jobId the id of the job to look up, as returned by {@link #startJob}
     * @return an ApiResponse wrapping the job's current status/progress/result
     */
    @GetMapping("/generate-flow/jobs/{jobId}")
    public ApiResponse<RecipeFlowGenerationJobResponseDTO> getJobStatus(@PathVariable String jobId) {
        RecipeFlowGenerationJobResponseDTO data = recipeFlowGenerationJobService.getJobStatus(jobId);
        return ApiResponse.<RecipeFlowGenerationJobResponseDTO>builder()
                .success(true)
                .message("Recipe flow generation job status")
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required to generate a recipe flow", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
