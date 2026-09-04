package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeSaveResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/v1/flows")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @PutMapping("/{flowId}")
    public ApiResponse<RecipeSaveResponseDTO> saveFlow(@PathVariable String flowId,
                                                       @RequestBody RecipeFlowSaveRequestDTO request) {
        request.setFlowId(flowId);
        return build(recipeService.saveFlow(request), "saved");
    }

    @GetMapping("/{flowId}")
    public ApiResponse<Optional<Recipe>> getFlow(@PathVariable String flowId) {
        return build(recipeService.getFlow(flowId), "fetched");
    }

    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
