package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessIngredientUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeProcessIngredientUsageService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-ingredient-usage")
public class RecipeProcessIngredientUsageController {

    @Autowired
    private RecipeProcessIngredientUsageService service;

    @PostMapping
    public ApiResponse<RecipeProcessIngredientUsageResponseDTO> create(@RequestBody RecipeProcessIngredientUsageRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeProcessIngredientUsageResponseDTO>> get(@PathVariable Long processId){
        return build(service.getByProcess(processId), "fetched");
    }

    private <T> ApiResponse<T> build(T data, String msg){
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

