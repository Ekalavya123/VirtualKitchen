package com.processVisualisation.virtualKitchen.controller.recipe;

import com.processVisualisation.virtualKitchen.dto.RecipeVisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.service.ai.RecipeVisualizationService;
import com.processVisualisation.virtualKitchen.utils.ApiResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/recipes")
public class RecipeVisualizationController {

    private final RecipeVisualizationService recipeVisualizationService;

    public RecipeVisualizationController(RecipeVisualizationService recipeVisualizationService) {
        this.recipeVisualizationService = recipeVisualizationService;
    }

    @PostMapping("/{recipeId}/visualization/generate")
    public ApiResponse<RecipeVisualizationResponseDTO> generate(@PathVariable String recipeId) {
        RecipeVisualizationResponseDTO data = recipeVisualizationService.generateVisualization(recipeId);
        return ApiResponse.<RecipeVisualizationResponseDTO>builder()
                .success(true)
                .message(data.getMessage())
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
