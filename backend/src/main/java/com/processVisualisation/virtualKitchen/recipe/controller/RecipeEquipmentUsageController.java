package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeEquipmentUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessEquipmentUsageService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for recording and querying which pieces of equipment were used during a
 * recipe (process) execution. No ownership check is performed here; the underlying process
 * execution id is trusted as provided by the caller.
 */
@RestController
@RequestMapping("/api/v1/process-equipment-usage")
public class RecipeEquipmentUsageController {

    @Autowired
    private IProcessEquipmentUsageService service;

    /**
     * Records a new equipment usage entry.
     *
     * @param dto the equipment usage details to persist (process execution id, equipment, etc.)
     * @return an ApiResponse wrapping the created equipment usage record
     */
    @PostMapping
    public ApiResponse<RecipeEquipmentUsageResponseDTO> create(@RequestBody RecipeEquipmentUsageRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Retrieves all equipment usage records logged for a given process execution.
     *
     * @param processId the id of the process execution to filter by
     * @return an ApiResponse wrapping the matching equipment usage records
     */
    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeEquipmentUsageResponseDTO>> get(@PathVariable Long processId){
        return build(service.getByProcess(processId), "fetched");
    }

    /**
     * Wraps a payload and status message into a standard success ApiResponse, stamped with the
     * current timestamp.
     *
     * @param data the response payload to wrap
     * @param msg  the status message describing the operation performed
     * @param <T>  the type of the response payload
     * @return the wrapped ApiResponse
     */
    private <T> ApiResponse<T> build(T data, String msg){
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
