package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeExecutionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessExecutionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for starting, transitioning, and querying recipe (process) executions - the
 * runtime instances created when a user runs a recipe/process template. No ownership check is
 * performed here; endpoints operate on the ids provided by the caller.
 */
@RestController
@RequestMapping("/api/v1/process-executions")
public class RecipeExecutionController {

    @Autowired
    private IProcessExecutionService service;

    /**
     * Starts a new recipe execution.
     *
     * @param dto the details needed to start the execution (user id, template id, etc.)
     * @return an ApiResponse wrapping the newly created execution
     */
    @PostMapping
    public ApiResponse<RecipeExecutionResponseDTO> start(@RequestBody RecipeExecutionRequestDTO dto){
        return build(service.start(dto), "started");
    }

    /**
     * Transitions an existing recipe execution to a new status.
     *
     * @param id     the id of the execution to update
     * @param status the new status, expected to match a RecipeStatus enum constant name
     * @return an ApiResponse wrapping the updated execution
     * @throws java.util.NoSuchElementException if no execution exists with the given id
     * @throws IllegalArgumentException if status does not match a known RecipeStatus constant
     */
    @PutMapping("/{id}/{status}")
    public ApiResponse<RecipeExecutionResponseDTO> updateStatus(@PathVariable Long id,
                                                                @PathVariable String status){
        return build(service.updateStatus(id, status), "updated");
    }

    /**
     * Retrieves all recipe executions started by a given user.
     *
     * @param userId the id of the user to filter by
     * @return an ApiResponse wrapping the executions started by that user
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecipeExecutionResponseDTO>> get(@PathVariable Long userId){
        return build(service.getByUser(userId), "fetched");
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
