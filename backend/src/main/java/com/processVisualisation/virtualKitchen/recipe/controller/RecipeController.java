package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeSaveResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.service.RecipeService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * REST controller for persisting and retrieving the React Flow-style process visualization
 * graph (nodes, edges, viewport) associated with a recipe. Backed by RecipeService, which
 * stores the graph as a Recipe document keyed by flow id. No ownership check is performed
 * here; save/get operate purely on the supplied flow id.
 */
@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/v1/flows")
public class RecipeController {

    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    /**
     * Creates or updates the saved flow (nodes, edges, viewport) for the given flow id, using
     * the request body contents while overriding its flow id with the path variable.
     *
     * @param flowId  the id of the flow to save, taken from the URL path
     * @param request the flow payload (nodes, edges, viewport, user/template ids) to persist
     * @return an ApiResponse wrapping the saved flow id, user id, and a status message
     */
    @PutMapping("/{flowId}")
    public ApiResponse<RecipeSaveResponseDTO> saveFlow(@PathVariable String flowId,
                                                       @RequestBody RecipeFlowSaveRequestDTO request) {
        request.setFlowId(flowId);
        return build(recipeService.saveFlow(request), "saved");
    }

    /**
     * Retrieves the saved flow document for the given flow id, if one exists.
     *
     * @param flowId the id of the flow to look up
     * @return an ApiResponse wrapping an Optional containing the Recipe document, empty if no
     *         flow is saved under that id
     */
    @GetMapping("/{flowId}")
    public ApiResponse<Optional<Recipe>> getFlow(@PathVariable String flowId) {
        return build(recipeService.getFlow(flowId), "fetched");
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
    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
