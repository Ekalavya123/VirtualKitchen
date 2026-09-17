package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.RecipeFlowGenerationJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link RecipeFlowGenerationJob},
 * persisting the progress and results of asynchronous recipe-flow generation
 * runs in the {@code recipe_flow_generation_job} collection.
 */
public interface RecipeFlowGenerationJobRepository extends MongoRepository<RecipeFlowGenerationJob, String> {

    /**
     * Looks up a previously started job by its client-supplied idempotency
     * key, so a double-submit of the same generation click returns the
     * existing job instead of starting (and charging credits for) a second
     * one.
     */
    Optional<RecipeFlowGenerationJob> findByUserIdAndClientRequestId(Long userId, String clientRequestId);
}
