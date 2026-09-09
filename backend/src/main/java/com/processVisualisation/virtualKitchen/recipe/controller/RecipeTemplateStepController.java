package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateStepResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessTemplateStepService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for creating and retrieving the ordered steps that make up a recipe/process
 * template.
 */
@RestController
@RequestMapping("/api/v1/process-template-steps")
public class RecipeTemplateStepController {

    @Autowired
    private IProcessTemplateStepService service;

    /**
     * Creates a new step for a recipe template.
     *
     * @param dto the step details to persist (owning template id, step order, etc.)
     * @return an ApiResponse wrapping the created step
     */
    @PostMapping
    public ApiResponse<RecipeTemplateStepResponseDTO> create(@RequestBody RecipeTemplateStepRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Retrieves all steps for a given recipe template, in step order.
     *
     * @param templateId the id of the owning template
     * @return an ApiResponse wrapping the template steps, ordered by step order
     */
    @GetMapping("/{templateId}")
    public ApiResponse<List<RecipeTemplateStepResponseDTO>> get(@PathVariable Long templateId){
        return build(service.getSteps(templateId), "fetched");
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
