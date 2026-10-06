package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;

import java.util.Collection;
import java.util.List;

/**
 * Spring Data MongoDB repository for {@link Process} documents (a recipe's
 * MAIN process or one of its nested SUBPROCESS documents).
 */
public interface ProcessRepository extends MongoRepository<Process, Long> {

    /**
     * Fetches every process (MAIN and any SUBPROCESS documents) belonging to a recipe.
     *
     * @param recipeId the id of the owning recipe
     * @return the matching processes
     */
    List<Process> findByRecipeId(Long recipeId);

    /**
     * Fetches every process belonging to any of the given recipes in one query (used to resolve
     * a whole recipe list's thumbnails at once).
     *
     * @param recipeIds the ids of the owning recipes
     * @return the matching processes
     */
    List<Process> findByRecipeIdIn(Collection<Long> recipeIds);

    /**
     * Fetches every process of a given type belonging to a recipe, used to check for
     * (at most one) existing MAIN process before creating another.
     *
     * @param recipeId the id of the owning recipe
     * @param type     the process type to filter by
     * @return the matching processes
     */
    List<Process> findByRecipeIdAndType(Long recipeId, ProcessType type);

    /**
     * Atomically writes one STEP node's generated visualization fields in place (positional
     * update on the node whose {@code id} matches), without rewriting the rest of the process
     * document — so an editor save landing while a visuals job finishes is never overwritten, and
     * a process deleted meanwhile is not re-created (no upsert).
     *
     * @return the number of processes updated: 0 if the process or the node no longer exists
     */
    @Query("{ '_id': ?0, 'nodes.id': ?1 }")
    @Update("{ '$set': { 'nodes.$.data.visualizationAssetId': ?2, 'nodes.$.data.imagePrompt': ?3, 'nodes.$.data.imageUrl': ?4 } }")
    long setStepVisualization(Long processId, String nodeId, Long visualizationAssetId, String imagePrompt, String imageUrl);
}
