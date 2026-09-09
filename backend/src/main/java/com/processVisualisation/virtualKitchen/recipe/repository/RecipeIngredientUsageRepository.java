package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredientUsage;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link RecipeIngredientUsage} documents, which record which
 * ingredients (and quantities) were consumed during a given recipe (process) execution.
 */
public interface RecipeIngredientUsageRepository extends MongoRepository<RecipeIngredientUsage, Long> {

    /**
     * Fetches all ingredient usage records logged against a given process execution.
     *
     * @param processExecutionId the id of the process execution to filter by
     * @return the ingredient usage records for that execution
     */
    List<RecipeIngredientUsage> findByProcessExecutionId(Long processExecutionId);
}
