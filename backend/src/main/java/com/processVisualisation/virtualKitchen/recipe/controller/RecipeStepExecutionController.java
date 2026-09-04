package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeStepExecutionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/step-executions")
public class RecipeStepExecutionController {

    @Autowired
    private RecipeStepExecutionService service;

    @PostMapping
    public ApiResponse<RecipeStepExecutionResponseDTO> create(@RequestBody RecipeStepExecutionRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @PutMapping("/{id}")
    public ApiResponse<RecipeStepExecutionResponseDTO> update(@PathVariable Long id,
                                                        @RequestBody RecipeStepExecutionUpdateDTO dto){
        return build(service.update(id, dto), "updated");
    }

    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeStepExecutionResponseDTO>> get(@PathVariable Long processId){
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

