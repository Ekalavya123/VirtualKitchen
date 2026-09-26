package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessBatchItemDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessUpdateDTO;

import java.util.List;

/**
 * Service contract for creating, reading, updating, deleting, and
 * (recipe-scoped) copying {@code Process} documents — the new Recipe Tool's
 * MAIN/SUBPROCESS graph model. Every method is scoped to a specific recipe
 * ({@code recipeId}); a process that exists but does not belong to that
 * recipe is treated as not found. Mutating methods enforce that the
 * requesting user owns the recipe; read methods follow the recipe's
 * existing {@code Visibility} (owner can always read; a non-owner can read
 * only if the recipe is {@code PUBLIC}).
 */
public interface IProcessService {

    /**
     * Creates a new process (MAIN or SUBPROCESS, per {@code dto.getType()})
     * under a recipe, after verifying the requesting user owns it and
     * validating the result via {@code ProcessValidator} (which rejects,
     * among other things, a second MAIN process for the same recipe).
     *
     * @param recipeId the id of the owning recipe
     * @param userId   the id of the user requesting the creation, used for ownership verification
     * @param dto      the process type/name/description to persist
     * @return the created process
     */
    ProcessResponseDTO create(Long recipeId, Long userId, ProcessRequestDTO dto);

    /**
     * Retrieves a single process by id, scoped to a recipe.
     *
     * @param recipeId         the id of the recipe the process is expected to belong to
     * @param processId        the id of the process to fetch
     * @param requestingUserId the id of the requesting user, or null if unauthenticated; used for
     *                         visibility on a private recipe
     * @return the matching process
     */
    ProcessResponseDTO get(Long recipeId, Long processId, Long requestingUserId);

    /**
     * Retrieves every process (MAIN and any SUBPROCESS documents) belonging to a recipe.
     *
     * @param recipeId         the id of the owning recipe
     * @param requestingUserId the id of the requesting user, or null if unauthenticated; used for
     *                         visibility on a private recipe
     * @return the recipe's processes
     */
    List<ProcessResponseDTO> listByRecipe(Long recipeId, Long requestingUserId);

    /**
     * Updates a process's name/description and replaces its node/edge graph
     * and viewport, after verifying the requesting user owns the recipe and
     * validating the result via {@code ProcessValidator}.
     *
     * @param recipeId  the id of the recipe the process is expected to belong to
     * @param processId the id of the process to update
     * @param userId    the id of the user requesting the update, used for ownership verification
     * @param dto       the replacement name/description/nodes/edges/viewport
     * @return the updated process
     */
    ProcessResponseDTO update(Long recipeId, Long processId, Long userId, ProcessUpdateDTO dto);

    /**
     * Recipe-level save: replaces name/description/nodes/edges/viewport for every process named in
     * {@code items} (typically the recipe's MAIN process plus every SUBPROCESS the Recipe Tool's
     * unified editing session currently has loaded), after verifying the requesting user owns the
     * recipe. Every item is validated via {@code ProcessValidator} before any of them are persisted —
     * one invalid process rejects the whole batch — so the Recipe Tool can expose this as a single
     * "Save" action instead of one independent save per process.
     *
     * @param recipeId the id of the recipe every listed process is expected to belong to
     * @param userId   the id of the user requesting the update, used for ownership verification
     * @param items    the replacement content for each process, keyed by its own {@code processId}
     * @return the updated processes, in the same order as {@code items}
     */
    List<ProcessResponseDTO> updateAll(Long recipeId, Long userId, List<ProcessBatchItemDTO> items);

    /**
     * Deletes a process, after verifying the requesting user owns the
     * recipe. Rejects deleting the recipe's MAIN process while it is still
     * referenced by {@code RecipeTemplate.mainProcessId}, and rejects
     * deleting any process still referenced by a PROCESS-kind node in
     * another process belonging to the same recipe.
     *
     * @param recipeId  the id of the recipe the process is expected to belong to
     * @param processId the id of the process to delete
     * @param userId    the id of the user requesting the deletion, used for ownership verification
     */
    void delete(Long recipeId, Long processId, Long userId);

    /**
     * Copy-on-insert reuse (Phase 1 scope): deep-clones a SUBPROCESS
     * (recursively, including any nested subprocess references) into a new,
     * independent process within the same recipe. Does not create a live
     * link to the source process. The MAIN process cannot be copied.
     *
     * @param recipeId  the id of the recipe both the source and the copy belong to
     * @param processId the id of the SUBPROCESS to copy
     * @param userId    the id of the user requesting the copy, used for ownership verification
     * @return the newly created copy
     */
    ProcessResponseDTO copy(Long recipeId, Long processId, Long userId);

    /**
     * Recipe copy: deep-clones every process of {@code sourceRecipeId} (MAIN and every SUBPROCESS)
     * into {@code targetRecipeId} as new, independent documents. Every copy gets a new process id
     * and new node/edge ids, and every STEP's Action On subprocess reference is rewritten to the
     * matching copy, so nothing in the target recipe points back at the source recipe (a reference
     * to a process outside the source recipe is dropped). Every copy is validated before any of
     * them are saved. Performs no ownership checks — callers (recipe copy) are responsible for
     * authorizing both recipes.
     *
     * @param sourceRecipeId the recipe whose processes are copied
     * @param targetRecipeId the (already persisted) recipe the copies belong to
     * @return the source-to-copy process id map, and the id of the copied MAIN process
     */
    RecipeProcessCopyResult copyAllToRecipe(Long sourceRecipeId, Long targetRecipeId);
}
