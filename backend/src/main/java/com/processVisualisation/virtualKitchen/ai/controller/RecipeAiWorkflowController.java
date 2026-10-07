package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.workflow.RecipeAiWorkflowService;
import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiTaskSelectionDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiWorkflowApproveRequestDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiWorkflowCreateRequestDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiWorkflowEstimateDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.dto.RecipeAiWorkflowResponseDTO;
import com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiTaskType;
import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
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
 * REST controller for AI Recipe Creation: one user-controlled workflow over process generation,
 * step visuals and narration. The recipe process is generated first; visuals and narration start
 * only when the user approves the (possibly edited) process. Poll {@link #getStatus} for progress;
 * {@code GET /api/v1/recipes/{recipeId}/jobs/active} also returns the open workflow after a reload.
 */
@RestController
@RequestMapping("/api/v1/recipes/{recipeId}/ai-workflows")
public class RecipeAiWorkflowController {

    private final RecipeAiWorkflowService workflowService;

    public RecipeAiWorkflowController(RecipeAiWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    /** Approximate credits for a task selection, before anything is started. */
    @PostMapping("/estimate")
    public ApiResponse<RecipeAiWorkflowEstimateDTO> estimate(@PathVariable Long recipeId, @RequestBody RecipeAiTaskSelectionDTO selection) {
        return build(workflowService.estimate(currentUserId(), recipeId, selection), "AI usage estimate");
    }

    /** Starts AI Recipe Creation, or returns the recipe's already-open workflow ({@code reused=true}). */
    @PostMapping
    public ApiResponse<RecipeAiWorkflowResponseDTO> create(@PathVariable Long recipeId, @Valid @RequestBody RecipeAiWorkflowCreateRequestDTO request) {
        return build(workflowService.create(currentUserId(), recipeId, request), "AI Recipe Creation started");
    }

    @GetMapping("/{workflowId}")
    public ApiResponse<RecipeAiWorkflowResponseDTO> getStatus(@PathVariable Long recipeId, @PathVariable String workflowId) {
        return build(workflowService.getStatus(currentUserId(), recipeId, workflowId), "AI Recipe Creation status");
    }

    /** What approving would cost now, priced against the recipe's currently saved steps. */
    @GetMapping("/{workflowId}/estimate")
    public ApiResponse<RecipeAiWorkflowEstimateDTO> estimateApproval(@PathVariable Long recipeId, @PathVariable String workflowId) {
        return build(workflowService.estimateApproval(currentUserId(), recipeId, workflowId), "AI usage estimate");
    }

    /** "Approve &amp; Continue": starts the selected visuals/narration from the saved, approved recipe. */
    @PostMapping("/{workflowId}/approve")
    public ApiResponse<RecipeAiWorkflowResponseDTO> approve(
            @PathVariable Long recipeId, @PathVariable String workflowId, @Valid @RequestBody RecipeAiWorkflowApproveRequestDTO request) {
        return build(workflowService.approve(currentUserId(), recipeId, workflowId, request.getApprovedRevision()),
                "Recipe process approved");
    }

    /** Retries only the failed part of one task. */
    @PostMapping("/{workflowId}/tasks/{task}/retry")
    public ApiResponse<RecipeAiWorkflowResponseDTO> retryTask(
            @PathVariable Long recipeId, @PathVariable String workflowId, @PathVariable RecipeAiTaskType task) {
        return build(workflowService.retryTask(currentUserId(), recipeId, workflowId, task), "Task retry started");
    }

    /** Discards a workflow waiting for approval (the only point where nothing is still running). */
    @PostMapping("/{workflowId}/discard")
    public ApiResponse<RecipeAiWorkflowResponseDTO> discard(@PathVariable Long recipeId, @PathVariable String workflowId) {
        return build(workflowService.discard(currentUserId(), recipeId, workflowId), "AI Recipe Creation discarded");
    }

    /** Hides a finished workflow's summary. */
    @PostMapping("/{workflowId}/dismiss")
    public ApiResponse<Void> dismiss(@PathVariable Long recipeId, @PathVariable String workflowId) {
        workflowService.dismiss(currentUserId(), recipeId, workflowId);
        return build(null, "AI Recipe Creation dismissed");
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication() != null
                ? SecurityContextHolder.getContext().getAuthentication().getPrincipal()
                : null;
        if (!(principal instanceof Long userId)) {
            throw new AuthException("Authentication required for AI Recipe Creation", HttpStatus.UNAUTHORIZED);
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
