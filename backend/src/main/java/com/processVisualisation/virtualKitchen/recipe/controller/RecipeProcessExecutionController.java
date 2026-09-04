package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeProcessExecutionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-executions")
public class RecipeProcessExecutionController {

    @Autowired
    private RecipeProcessExecutionService service;

    @PostMapping
    public ApiResponse<RecipeProcessExecutionResponseDTO> start(@RequestBody RecipeProcessExecutionRequestDTO dto){
        return build(service.start(dto), "started");
    }

    @PutMapping("/{id}/{status}")
    public ApiResponse<RecipeProcessExecutionResponseDTO> updateStatus(@PathVariable Long id,
                                                                @PathVariable String status){
        return build(service.updateStatus(id, status), "updated");
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecipeProcessExecutionResponseDTO>> get(@PathVariable Long userId){
        return build(service.getByUser(userId), "fetched");
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

