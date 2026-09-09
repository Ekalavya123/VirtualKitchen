package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientUsageResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessIngredientUsageService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for recording and querying which ingredients were consumed during a recipe
 * (process) execution. No ownership check is performed here; the underlying process execution
 * id is trusted as provided by the caller.
 */
@RestController
@RequestMapping("/api/v1/process-ingredient-usage")
public class RecipeIngredientUsageController {

    @Autowired
    private IProcessIngredientUsageService service;

    /**
     * Records a new ingredient usage entry.
     *
     * @param dto the ingredient usage details to persist (process execution id, ingredient, etc.)
     * @return an ApiResponse wrapping the created ingredient usage record
     */
    @PostMapping
    public ApiResponse<RecipeIngredientUsageResponseDTO> create(@RequestBody RecipeIngredientUsageRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Retrieves all ingredient usage records logged for a given process execution.
     *
     * @param processId the id of the process execution to filter by
     * @return an ApiResponse wrapping the matching ingredient usage records
     */
    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeIngredientUsageResponseDTO>> get(@PathVariable Long processId){
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
