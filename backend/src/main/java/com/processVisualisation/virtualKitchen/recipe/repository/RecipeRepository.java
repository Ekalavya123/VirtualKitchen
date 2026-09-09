package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link Recipe} documents, which store the process-flow
 * visualization graph (nodes, edges, viewport) for a recipe, keyed by flow id.
 */
public interface RecipeRepository extends MongoRepository<Recipe, String> {

    /**
     * Fetches the saved flow document for a given flow id.
     *
     * @param flowId the flow id to look up
     * @return the matching {@link Recipe} document, if one exists
     */
    Optional<Recipe> findByFlowId(String flowId);

    /**
     * Checks whether a flow document exists for a given flow id.
     *
     * @param flowId the flow id to check
     * @return {@code true} if a document exists for that flow id, {@code false} otherwise
     */
    boolean existsByFlowId(String flowId);
}
