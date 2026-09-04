package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveResponseDTO;
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

    private final RecipeService flowService;

    public RecipeController(RecipeService flowService) {
        this.flowService = flowService;
    }

    @PutMapping("/{flowId}")
    public ApiResponse<RecipeFlowSaveResponseDTO> saveFlow(@PathVariable String flowId,
                                                     @RequestBody RecipeFlowSaveRequestDTO request) {
        request.setFlowId(flowId);
        return build(flowService.saveFlow(request), "saved");
    }

    @GetMapping("/{flowId}")
    public ApiResponse<Optional<Recipe>> getFlow(@PathVariable String flowId) {
        return build(flowService.getFlow(flowId), "fetched");
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
