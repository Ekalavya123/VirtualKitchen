package com.processVisualisation.virtualKitchen.store.controller;

import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;
import com.processVisualisation.virtualKitchen.store.service.IItemCostService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/item-cost")
public class ItemCostController {

    @Autowired
    private IItemCostService service;

    @PostMapping
    public ApiResponse<ItemCostResponseDTO> create(@RequestBody ItemCostRequestDTO dto){
        return ApiResponse.<ItemCostResponseDTO>builder()
                .success(true)
                .message("Cost created")
                .data(service.create(dto))
                .timestamp(LocalDateTime.now())
                .build();
    }
}
