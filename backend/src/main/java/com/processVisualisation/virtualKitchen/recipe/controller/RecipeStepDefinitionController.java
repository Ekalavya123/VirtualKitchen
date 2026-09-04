package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeStepDefinitionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/step-definitions")
public class RecipeStepDefinitionController {

    @Autowired
    private RecipeStepDefinitionService service;

    @PostMapping
    public ApiResponse<RecipeStepDefinitionResponseDTO> create(@RequestBody RecipeStepDefinitionRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping
    public ApiResponse<List<RecipeStepDefinitionResponseDTO>> getAll(){
        return build(service.getAll(), "fetched");
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

