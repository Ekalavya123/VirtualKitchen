package com.processVisualisation.virtualKitchen.store.controller;

import com.processVisualisation.virtualKitchen.store.dto.IngredientRequestDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientResponseDTO;
import com.processVisualisation.virtualKitchen.store.dto.IngredientUpdateDTO;
import com.processVisualisation.virtualKitchen.store.service.IIngredientService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller exposing CRUD endpoints for the ingredient catalog under
 * {@code /api/v1/ingredients}. Delegates persistence and business logic to
 * {@link IIngredientService} and wraps every response in a standard
 * {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/ingredients")
public class IngredientController {

    @Autowired
    private IIngredientService ingredientService;

    /**
     * Creates a new ingredient catalog entry.
     *
     * @param request the validated ingredient name, description and default unit
     * @return a 200 response wrapping the created ingredient
     * @throws RuntimeException if an ingredient with the same name already exists
     */
    @PostMapping
    public ResponseEntity<ApiResponse<IngredientResponseDTO>> create(
            @Valid @RequestBody IngredientRequestDTO request) {

        return ResponseEntity.ok(
                buildResponse(true, "Ingredient created",
                        ingredientService.createIngredient(request))
        );
    }

    /**
     * Fetches a single ingredient by its identifier.
     *
     * @param id the ingredient identifier
     * @return a 200 response wrapping the matching ingredient
     * @throws RuntimeException if no ingredient exists with the given id
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponseDTO>> getById(@PathVariable Long id) {

        return ResponseEntity.ok(
                buildResponse(true, "Ingredient fetched",
                        ingredientService.getIngredientById(id))
        );
    }

    /**
     * Fetches every ingredient in the catalog.
     *
     * @return a 200 response wrapping the list of all ingredients
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<IngredientResponseDTO>>> getAll() {

        return ResponseEntity.ok(
                buildResponse(true, "All ingredients fetched",
                        ingredientService.getAllIngredients())
        );
    }

    /**
     * Updates an existing ingredient's name, description and default unit.
     *
     * @param id the identifier of the ingredient to update
     * @param request the validated replacement values
     * @return a 200 response wrapping the updated ingredient
     * @throws RuntimeException if no ingredient exists with the given id
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<IngredientResponseDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody IngredientUpdateDTO request) {

        return ResponseEntity.ok(
                buildResponse(true, "Ingredient updated",
                        ingredientService.updateIngredient(id, request))
        );
    }

    /**
     * Deletes an ingredient from the catalog.
     *
     * @param id the identifier of the ingredient to delete
     * @return a 200 response with no data payload confirming deletion
     * @throws RuntimeException if no ingredient exists with the given id
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {

        ingredientService.deleteIngredient(id);

        return ResponseEntity.ok(
                buildResponse(true, "Ingredient deleted", null)
        );
    }

    private <T> ApiResponse<T> buildResponse(boolean success, String message, T data) {
        return ApiResponse.<T>builder()
                .success(success)
                .message(message)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}