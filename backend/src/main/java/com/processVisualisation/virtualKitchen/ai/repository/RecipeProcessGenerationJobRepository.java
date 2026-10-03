package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJob;
import com.processVisualisation.virtualKitchen.ai.model.RecipeProcessGenerationJobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
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

    /** The job currently running for a user+recipe, if any (see {@link RecipeProcessGenerationJob#getActiveKey()}). */
    Optional<RecipeProcessGenerationJob> findByActiveKey(String activeKey);

    /**
     * The newest completed job for a user+recipe whose result the frontend hasn't loaded yet —
     * offered back to a user who navigated away (or reloaded) while generation was running.
     */
    Optional<RecipeProcessGenerationJob> findFirstByUserIdAndRecipeIdAndStatusAndResultAppliedAtIsNullAndCompletedAtAfterOrderByCompletedAtDesc(
            Long userId, Long recipeId, RecipeProcessGenerationJobStatus status, Instant completedAfter);
}
