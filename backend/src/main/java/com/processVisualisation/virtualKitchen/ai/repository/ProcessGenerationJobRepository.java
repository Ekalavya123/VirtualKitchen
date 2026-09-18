package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.ProcessGenerationJob;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link ProcessGenerationJob}, persisting
 * the progress and results of asynchronous AI Process generation runs in the
 * {@code process_generation_job} collection.
 */
public interface ProcessGenerationJobRepository extends MongoRepository<ProcessGenerationJob, String> {

    /**
     * Looks up a previously started job by its client-supplied idempotency key, so a double-submit
     * of the same generation click returns the existing job instead of starting a duplicate.
     */
    Optional<ProcessGenerationJob> findByUserIdAndClientRequestId(Long userId, String clientRequestId);
}
