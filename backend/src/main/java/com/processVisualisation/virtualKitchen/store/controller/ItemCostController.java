package com.processVisualisation.virtualKitchen.store.controller;

import com.processVisualisation.virtualKitchen.store.dto.ItemCostRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.ItemCostResponseDTO;
import com.processVisualisation.virtualKitchen.store.service.IItemCostService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST controller for recording item cost entries under
 * {@code /api/v1/item-cost}. A cost entry records the price per unit,
 * effective from a given date, for either an ingredient or a piece of
 * equipment (see {@link com.processVisualisation.virtualKitchen.store.model.ItemType}).
 * Delegates persistence to {@link IItemCostService} and wraps the response
 * in a standard {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/item-cost")
public class ItemCostController {

    @Autowired
    private IItemCostService service;

    /**
     * Creates a new cost record for an ingredient or equipment item.
     *
     * @param dto the item type, item id, unit, cost per unit, currency and effective date
     * @return an {@link ApiResponse} wrapping the created cost record
     */
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
