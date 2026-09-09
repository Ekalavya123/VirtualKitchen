package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.RecipeStepExecution;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link RecipeStepExecution} documents, which track the
 * runtime status (pending/in-progress/done, timestamps, notes) of each individual step within
 * a recipe (process) execution.
 */
public interface RecipeStepExecutionRepository extends MongoRepository<RecipeStepExecution, Long> {

    /**
     * Fetches all step executions belonging to a given process execution, ordered by their
     * document id ascending (i.e. the order in which the steps were created).
     *
     * @param processExecutionId the id of the process execution to filter by
     * @return the step executions for that process execution, ordered by id ascending
     */
    List<RecipeStepExecution> findByProcessExecutionIdOrderByIdAsc(Long processExecutionId);
}
