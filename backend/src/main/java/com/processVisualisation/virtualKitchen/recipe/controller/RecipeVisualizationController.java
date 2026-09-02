package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeVisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIRecipeVisualizationService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/recipes")
public class RecipeVisualizationController {

    private final AIRecipeVisualizationService AIRecipeVisualizationService;

    public RecipeVisualizationController(AIRecipeVisualizationService AIRecipeVisualizationService) {
        this.AIRecipeVisualizationService = AIRecipeVisualizationService;
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
}
