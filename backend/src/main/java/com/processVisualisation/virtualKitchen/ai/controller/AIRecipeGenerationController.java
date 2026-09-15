package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.common.exception.AuthException;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeGenerationService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing the AI-driven recipe flow generation endpoint under
 * {@code /api/recipe}. Delegates the actual flow generation logic to
 * {@link AIRecipeGenerationService}.
 */
@RestController
@RequestMapping("/api/recipe")
public class AIRecipeGenerationController {

    private final AIRecipeGenerationService AIRecipeGenerationService;

    public AIRecipeGenerationController(AIRecipeGenerationService AIRecipeGenerationService) {
        this.AIRecipeGenerationService = AIRecipeGenerationService;
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
