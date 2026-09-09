package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeGenerationService;

import jakarta.validation.Valid;
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
     * free-form recipe description using AI.
     *
     * @param request the validated request containing the raw recipe text
     * @return the generated recipe flow response
     */
    @PostMapping("/generate-flow")
    public RecipeFlowGenerationResponseDTO generateFlow(@Valid @RequestBody RecipeFlowGenerationRequestDTO request) {
        return AIRecipeGenerationService.generateFlow(request.getRecipe());
    }
}
