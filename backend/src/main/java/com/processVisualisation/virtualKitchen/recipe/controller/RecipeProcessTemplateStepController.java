package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessTemplateStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeProcessTemplateStepService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-template-steps")
public class RecipeProcessTemplateStepController {

    @Autowired
    private RecipeProcessTemplateStepService service;

    @PostMapping
    public ApiResponse<RecipeProcessTemplateStepResponseDTO> create(@RequestBody RecipeProcessTemplateStepRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping("/{templateId}")
    public ApiResponse<List<RecipeProcessTemplateStepResponseDTO>> get(@PathVariable Long templateId){
        return build(service.getSteps(templateId), "fetched");
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

