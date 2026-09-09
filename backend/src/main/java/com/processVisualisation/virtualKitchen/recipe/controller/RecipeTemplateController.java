package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.service.IProcessTemplateService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * REST controller for creating, reading, updating, deleting, and sharing recipe/process
 * templates. Recipe templates are user-owned: the update, updateVisibility, and delete
 * endpoints pass the requesting userId through to the service layer, which verifies ownership
 * before applying the change and rejects the request otherwise.
 */
@RestController
@RequestMapping("/api/v1/process-templates")
public class RecipeTemplateController {

    @Autowired
    private IProcessTemplateService service;

    /**
     * Creates a new recipe template.
     *
     * @param dto the template details to persist (name, description, owner, etc.)
     * @return an ApiResponse wrapping the created template
     */
    @PostMapping
    public ApiResponse<RecipeTemplateResponseDTO> create(@RequestBody RecipeTemplateRequestDTO dto){
        return build(service.create(dto), "created");
    }

    /**
     * Retrieves a single recipe template by id.
     *
     * @param id the id of the template to fetch
     * @return an ApiResponse wrapping the matching template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     */
    @GetMapping("/{id}")
    public ApiResponse<RecipeTemplateResponseDTO> get(@PathVariable Long id){
        return build(service.get(id), "fetched");
    }

    /**
     * Retrieves all recipe templates owned by a given user.
     *
     * @param userId the id of the owning user to filter by
     * @return an ApiResponse wrapping the templates created by that user
     */
    @GetMapping("/user/{userId}")
    public ApiResponse<List<RecipeTemplateResponseDTO>> getByUser(@PathVariable Long userId){
        return build(service.getByUser(userId), "fetched");
    }

    /**
     * Retrieves all publicly visible recipe templates that were not created by the given user,
     * i.e. the recipes available for that user to browse/copy from other authors.
     *
     * @param userId the id of the user to exclude as owner
     * @return an ApiResponse wrapping the matching public templates owned by other users
     */
    @GetMapping("/global/{userId}")
    public ApiResponse<List<RecipeTemplateResponseDTO>> getGlobalRecipes(@PathVariable Long userId){
        return build(service.getGlobalRecipes(userId), "fetched");
    }

    /**
     * Updates the name and description of a recipe template. The caller must own the template;
     * ownership is verified in the service layer using the supplied userId.
     *
     * @param id     the id of the template to update
     * @param userId the id of the user requesting the update, used for ownership verification
     * @param dto    the new name/description values
     * @return an ApiResponse wrapping the updated template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException if userId does not own the template
     */
    @PutMapping("/{id}")
    public ApiResponse<RecipeTemplateResponseDTO> update(@PathVariable Long id,
                                                         @RequestParam Long userId,
                                                         @RequestBody RecipeTemplateUpdateDTO dto){
        return build(service.update(id, userId, dto), "updated");
    }

    /**
     * Updates the visibility (e.g. private/public) of a recipe template. The caller must own
     * the template; ownership is verified in the service layer using the supplied userId.
     *
     * @param id         the id of the template to update
     * @param userId     the id of the user requesting the update, used for ownership verification
     * @param visibility the new visibility value
     * @return an ApiResponse wrapping the updated template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException if userId does not own the template
     */
    @PutMapping("/{id}/visibility")
    public ApiResponse<RecipeTemplateResponseDTO> updateVisibility(@PathVariable Long id,
                                                                   @RequestParam Long userId,
                                                                   @RequestParam Visibility visibility){
        return build(service.updateVisibility(id, userId, visibility), "updated");
    }

    /**
     * Copies a publicly visible recipe template (and its saved flow graph) into a new private
     * template owned by the given user.
     *
     * @param id     the id of the public template to copy
     * @param userId the id of the user the copy will be owned by
     * @return an ApiResponse wrapping the newly created copy
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException if the source template is not public
     */
    @PostMapping("/{id}/copy")
    public ApiResponse<RecipeTemplateResponseDTO> copy(@PathVariable Long id,
                                                       @RequestParam Long userId){
        return build(service.copyToUser(id, userId), "created");
    }

    /**
     * Deletes a recipe template. The caller must own the template; ownership is verified in the
     * service layer using the supplied userId.
     *
     * @param id     the id of the template to delete
     * @param userId the id of the user requesting the deletion, used for ownership verification
     * @return an ApiResponse with no data, confirming deletion
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException if userId does not own the template
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam Long userId){
        service.delete(id, userId);
        return build(null, "deleted");
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
