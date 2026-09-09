package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeStepDefinitionResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IStepDefinitionService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for creating and listing the reusable catalog of step definitions (e.g.
 * named cooking actions) that recipe template steps and step executions reference.
 */
@RestController
@RequestMapping("/api/v1/step-definitions")
public class RecipeStepDefinitionController {

    @Autowired
    private IStepDefinitionService service;

    /**
     * Creates a new step definition.
     *
     * @param dto the step definition details to persist
     * @return an ApiResponse wrapping the created step definition
     */
    @PostMapping
    public ApiResponse<RecipeStepDefinitionResponseDTO> create(@RequestBody RecipeStepDefinitionRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Retrieves every step definition in the catalog.
     *
     * @return an ApiResponse wrapping all known step definitions
     */
    @GetMapping
    public ApiResponse<List<RecipeStepDefinitionResponseDTO>> getAll(){
        return build(service.getAll(), "fetched");
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
