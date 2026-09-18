package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import org.springframework.data.mongodb.repository.MongoRepository;

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
     * Fetches every process of a given type belonging to a recipe, used to check for
     * (at most one) existing MAIN process before creating another.
     *
     * @param recipeId the id of the owning recipe
     * @param type     the process type to filter by
     * @return the matching processes
     */
    List<Process> findByRecipeIdAndType(Long recipeId, ProcessType type);
}
