package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link RecipeProcessGenerationJob}, persisting
 * the progress and results of asynchronous AI Process generation runs in the
 * {@code process_generation_job} collection.
 */
public interface RecipeProcessGenerationJobRepository extends MongoRepository<RecipeProcessGenerationJob, String> {

    /**
     * Looks up a previously started job by its client-supplied idempotency key, so a double-submit
     * of the same generation click returns the existing job instead of starting a duplicate.
     */
    Optional<RecipeProcessGenerationJob> findByUserIdAndClientRequestId(Long userId, String clientRequestId);
}
