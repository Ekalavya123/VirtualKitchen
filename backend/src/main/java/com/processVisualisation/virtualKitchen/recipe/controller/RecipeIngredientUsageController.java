package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessIngredientUsageService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-ingredient-usage")
public class RecipeIngredientUsageController {

    @Autowired
    private IProcessIngredientUsageService service;

    @PostMapping
    public ApiResponse<RecipeIngredientUsageResponseDTO> create(@RequestBody RecipeIngredientUsageRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeIngredientUsageResponseDTO>> get(@PathVariable Long processId){
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
