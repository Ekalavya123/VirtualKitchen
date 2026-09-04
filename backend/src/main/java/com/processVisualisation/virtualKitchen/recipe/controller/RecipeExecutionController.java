package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessExecutionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-executions")
public class RecipeExecutionController {

    @Autowired
    private IProcessExecutionService service;

    @PostMapping
    public ApiResponse<RecipeExecutionResponseDTO> start(@RequestBody RecipeExecutionRequestDTO dto){
        return build(service.start(dto), "started");
    }

    @PutMapping("/{id}/{status}")
    public ApiResponse<RecipeExecutionResponseDTO> updateStatus(@PathVariable Long id,
                                                                @PathVariable String status){
        return build(service.updateStatus(id, status), "updated");
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecipeExecutionResponseDTO>> get(@PathVariable Long userId){
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
