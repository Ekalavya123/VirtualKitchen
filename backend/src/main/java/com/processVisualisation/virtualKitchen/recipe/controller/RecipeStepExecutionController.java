package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepExecutionUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IStepExecutionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for creating, updating, and querying the runtime status of individual steps
 * within a recipe (process) execution. No ownership check is performed here; endpoints operate
 * on the ids provided by the caller.
 */
@RestController
@RequestMapping("/api/v1/step-executions")
public class RecipeStepExecutionController {

    @Autowired
    private IStepExecutionService service;

    /**
     * Creates a new step execution record.
     *
     * @param dto the step execution details to persist (owning process execution id, step, etc.)
     * @return an ApiResponse wrapping the created step execution
     */
    @PostMapping
    public ApiResponse<RecipeStepExecutionResponseDTO> create(@RequestBody RecipeStepExecutionRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Updates the status and notes of an existing step execution.
     *
     * @param id  the id of the step execution to update
     * @param dto the new status and notes to apply
     * @return an ApiResponse wrapping the updated step execution
     * @throws java.util.NoSuchElementException if no step execution exists with the given id
     * @throws IllegalArgumentException if the status in dto does not match a known RecipeStepStatus constant
     */
    @PutMapping("/{id}")
    public ApiResponse<RecipeStepExecutionResponseDTO> update(@PathVariable Long id,
                                                              @RequestBody RecipeStepExecutionUpdateDTO dto){
        return build(service.update(id, dto), "updated");
    }

    /**
     * Retrieves all step executions belonging to a given process execution, in creation order.
     *
     * @param processId the id of the owning process execution
     * @return an ApiResponse wrapping the step executions for that process execution
     */
    @GetMapping("/{processId}")
    public ApiResponse<List<RecipeStepExecutionResponseDTO>> get(@PathVariable Long processId){
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
