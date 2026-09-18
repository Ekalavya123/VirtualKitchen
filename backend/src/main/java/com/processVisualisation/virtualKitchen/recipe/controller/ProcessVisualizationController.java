package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationJobService;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.dto.VisualizationJobResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * REST controller for AI-driven visualization of one Process (MAIN or SUBPROCESS)'s own STEP
 * nodes, as an asynchronous background job that can be polled for progress — the Process-model
 * counterpart to {@link RecipeVisualizationController}'s legacy flow-model endpoints. Delegates
 * to {@link ProcessVisualizationJobService}.
 */
@RestController
@RequestMapping("/api/v1/recipes/{recipeId}/processes/{processId}/visualization")
public class ProcessVisualizationController {

    private final ProcessVisualizationJobService processVisualizationJobService;

    public ProcessVisualizationController(ProcessVisualizationJobService processVisualizationJobService) {
        this.processVisualizationJobService = processVisualizationJobService;
    }

    /**
     * Starts an async visualization job for this process's own steps and returns immediately —
     * the actual generation work runs on a background task pool. Poll {@link #getJobStatus} with
     * the returned jobId for progress/completion.
     */
    @PostMapping("/jobs")
    public ApiResponse<VisualizationJobResponseDTO> startJob(@PathVariable Long recipeId, @PathVariable Long processId) {
        VisualizationJobResponseDTO data = processVisualizationJobService.startJob(currentUserId(), recipeId, processId);
        return build(data, "Process visualization job started");
    }

    /**
     * Retrieves the current status (and, once complete, per-step results) of a previously started
     * process visualization job.
     */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<VisualizationJobResponseDTO> getJobStatus(
            @PathVariable Long recipeId, @PathVariable Long processId, @PathVariable String jobId) {
        VisualizationJobResponseDTO data = processVisualizationJobService.getJobStatus(jobId);
        return build(data, "Process visualization job status");
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required to generate a visualization", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private <T> ApiResponse<T> build(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
