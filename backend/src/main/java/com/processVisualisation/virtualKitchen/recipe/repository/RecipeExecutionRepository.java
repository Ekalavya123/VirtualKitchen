package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link RecipeExecution} documents, which track a single
 * run-through (execution instance) of a recipe/process template by a user, including its
 * status and completion timestamp.
 */
public interface RecipeExecutionRepository extends MongoRepository<RecipeExecution, Long> {

    /**
     * Fetches all recipe executions started by a given user.
     *
     * @param userId the id of the user to filter by
     * @return the executions started by that user
     */
    List<RecipeExecution> findByUserId(Long userId);

    /**
     * Fetches all recipe executions that were started from a given process/recipe template.
     *
     * @param processTemplateId the id of the process template to filter by
     * @return the executions started from that template
     */
    List<RecipeExecution> findByProcessTemplateId(Long processTemplateId);
}
