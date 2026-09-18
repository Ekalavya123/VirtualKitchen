package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.service.ProcessGenerationJobService;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessGenerationJobResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessGenerationRequestDTO;
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
 * REST controller exposing AI-driven Process generation (a semantic MAIN
 * process + subprocesses from free-form recipe text), scoped under the same
 * recipe-processes base path as {@code ProcessController}. Async-job only —
 * generation always involves an AI call, so there's no synchronous variant
 * (mirrors {@code AIRecipeGenerationController}'s job endpoints). The result
 * is never persisted by this controller/service; the frontend loads it into
 * the current Recipe working session and the user Saves explicitly.
 */
@RestController
@RequestMapping("/api/v1/recipes/{recipeId}/processes/generate")
public class AIProcessGenerationController {

    private final ProcessGenerationJobService processGenerationJobService;

    public AIProcessGenerationController(ProcessGenerationJobService processGenerationJobService) {
        this.processGenerationJobService = processGenerationJobService;
    }

    /**
     * Starts an async Process generation job and returns immediately — the actual AI call runs on
     * a background task pool. Poll {@link #getJobStatus} with the returned jobId for progress/completion.
     *
     * @param recipeId the id of the recipe this generation is for (not persisted against by this
     *                 endpoint — only used for job bookkeeping/analytics)
     * @param request  the validated request containing the raw recipe text
     * @return an ApiResponse wrapping the newly started job's id and initial status
     */
    @PostMapping("/jobs")
    public ApiResponse<ProcessGenerationJobResponseDTO> startJob(
            @PathVariable Long recipeId, @Valid @RequestBody ProcessGenerationRequestDTO request
    ) {
        Long userId = currentUserId();
        ProcessGenerationJobResponseDTO data = processGenerationJobService.startJob(
                userId, recipeId, request.getRecipeText(), request.getClientRequestId());
        return build(data, "Process generation job started");
    }

    /**
     * Retrieves the current status (and, once complete, result) of a previously started Process
     * generation job.
     *
     * @param jobId the id of the job to look up, as returned by {@link #startJob}
     * @return an ApiResponse wrapping the job's current status/progress/result
     */
    @GetMapping("/jobs/{jobId}")
    public ApiResponse<ProcessGenerationJobResponseDTO> getJobStatus(@PathVariable Long recipeId, @PathVariable String jobId) {
        ProcessGenerationJobResponseDTO data = processGenerationJobService.getJobStatus(jobId);
        return build(data, "Process generation job status");
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required to generate a recipe process", HttpStatus.UNAUTHORIZED);
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
