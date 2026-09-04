package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeProcessEquipmentUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeProcessEquipmentUsageService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/process-equipment-usage")
public class RecipeProcessEquipmentUsageController {

    @Autowired
    private RecipeProcessEquipmentUsageService service;

    @PostMapping
    public ApiResponse<RecipeProcessEquipmentUsageResponseDTO> create(@RequestBody RecipeProcessEquipmentUsageRequestDTO dto){
        return build(service.create(dto), "created");
    }

    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeProcessEquipmentUsageResponseDTO>> get(@PathVariable Long processId){
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

