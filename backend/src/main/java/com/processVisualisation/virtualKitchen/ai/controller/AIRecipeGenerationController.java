package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowGenerationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeGenerationService;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipe")
public class AIRecipeGenerationController {

    private final AIRecipeGenerationService AIRecipeGenerationService;

    public AIRecipeGenerationController(AIRecipeGenerationService AIRecipeGenerationService) {
        this.AIRecipeGenerationService = AIRecipeGenerationService;
    }

    @PostMapping("/generate-flow")
    public RecipeFlowGenerationResponseDTO generateFlow(@Valid @RequestBody RecipeFlowGenerationRequestDTO request) {
        return AIRecipeGenerationService.generateFlow(request.getRecipe());
    }
}
