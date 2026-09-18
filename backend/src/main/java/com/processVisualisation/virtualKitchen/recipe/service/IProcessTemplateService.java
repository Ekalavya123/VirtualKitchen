package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.NutritionInfoDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeDetailResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeIngredientDTO;
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

    /**
     * Retrieves the new Recipe Tool's detail view of a recipe (ingredients,
     * nutrition, main process id, plus the base fields), enforcing the
     * recipe's existing visibility: the owner can always read it; a
     * non-owner can read it only if it is {@link Visibility#PUBLIC}.
     *
     * @param recipeId         the id of the recipe to fetch
     * @param requestingUserId the id of the requesting user, or null if unauthenticated
     * @return the recipe's detail view
     */
    RecipeDetailResponseDTO getRecipeDetails(Long recipeId, Long requestingUserId);

    /**
     * Replaces a recipe's ingredient list, after verifying the requesting user owns it.
     *
     * @param recipeId    the id of the recipe to update
     * @param userId      the id of the user requesting the update, used for ownership verification
     * @param ingredients the replacement ingredient list
     * @return the updated recipe's detail view
     */
    RecipeDetailResponseDTO updateIngredients(Long recipeId, Long userId, List<RecipeIngredientDTO> ingredients);

    /**
     * Replaces a recipe's nutrition info, after verifying the requesting user owns it.
     *
     * @param recipeId  the id of the recipe to update
     * @param userId    the id of the user requesting the update, used for ownership verification
     * @param nutrition the replacement nutrition info
     * @return the updated recipe's detail view
     */
    RecipeDetailResponseDTO updateNutrition(Long recipeId, Long userId, NutritionInfoDTO nutrition);

    /**
     * Retrieves a recipe's MAIN process, following the same visibility rule as
     * {@link #getRecipeDetails}.
     *
     * @param recipeId         the id of the recipe whose main process to fetch
     * @param requestingUserId the id of the requesting user, or null if unauthenticated
     * @return the recipe's MAIN process
     * @throws java.util.NoSuchElementException if the recipe has no main process yet
     */
    ProcessResponseDTO getMainProcess(Long recipeId, Long requestingUserId);

    /**
     * Ensures a recipe has a MAIN process: if {@code mainProcessId} is
     * already set, returns that existing process unchanged (idempotent —
     * repeated calls never create a second MAIN process); otherwise creates
     * a new MAIN process and assigns it, after verifying the requesting
     * user owns the recipe.
     *
     * @param recipeId the id of the recipe to create/assign a main process for
     * @param userId   the id of the user requesting the creation, used for ownership verification
     * @return the recipe's (possibly newly created) MAIN process
     */
    ProcessResponseDTO createMainProcess(Long recipeId, Long userId);
}
