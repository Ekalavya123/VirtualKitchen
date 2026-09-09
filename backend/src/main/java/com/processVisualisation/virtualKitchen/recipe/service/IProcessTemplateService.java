package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;

import java.util.List;

/**
 * Service contract for creating, reading, updating, deleting, and sharing recipe/process
 * templates. Templates are user-owned; mutation methods (update, delete, updateVisibility) are
 * expected to enforce ownership before applying changes.
 */
public interface IProcessTemplateService {

    /**
     * Creates a new recipe template.
     *
     * @param dto the template details to persist (name, description, owner, etc.)
     * @return the created template
     */
    RecipeTemplateResponseDTO create(RecipeTemplateRequestDTO dto);

    /**
     * Retrieves a single recipe template by id.
     *
     * @param id the id of the template to fetch
     * @return the matching template
     */
    RecipeTemplateResponseDTO get(Long id);

    /**
     * Retrieves all recipe templates owned by a given user.
     *
     * @param userId the id of the owning user to filter by
     * @return the templates created by that user
     */
    List<RecipeTemplateResponseDTO> getByUser(Long userId);

    /**
     * Retrieves all publicly visible recipe templates that were not created by the given user,
     * i.e. the recipes available for that user to browse/copy from other authors.
     *
     * @param userId the id of the user to exclude as owner
     * @return the matching public templates owned by other users
     */
    List<RecipeTemplateResponseDTO> getGlobalRecipes(Long userId);

    /**
     * Updates the name and description of a recipe template, after verifying the requesting
     * user owns it.
     *
     * @param id     the id of the template to update
     * @param userId the id of the user requesting the update, used for ownership verification
     * @param dto    the new name/description values
     * @return the updated template
     */
    RecipeTemplateResponseDTO update(Long id, Long userId, RecipeTemplateUpdateDTO dto);

    /**
     * Deletes a recipe template, after verifying the requesting user owns it.
     *
     * @param id     the id of the template to delete
     * @param userId the id of the user requesting the deletion, used for ownership verification
     */
    void delete(Long id, Long userId);

    /**
     * Updates the visibility (e.g. private/public) of a recipe template, after verifying the
     * requesting user owns it.
     *
     * @param id         the id of the template to update
     * @param userId     the id of the user requesting the update, used for ownership verification
     * @param visibility the new visibility value
     * @return the updated template
     */
    RecipeTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility);

    /**
     * Copies a publicly visible recipe template (and its saved flow graph) into a new private
     * template owned by the given user.
     *
     * @param id     the id of the public template to copy
     * @param userId the id of the user the copy will be owned by
     * @return the newly created copy
     */
    RecipeTemplateResponseDTO copyToUser(Long id, Long userId);
}
