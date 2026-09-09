package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationJob;
import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Spring Data MongoDB repository for {@link VisualizationJob}, persisting the
 * progress and results of asynchronous recipe-visualization runs in the
 * {@code visualization_job} collection.
 */
public interface VisualizationJobRepository extends MongoRepository<VisualizationJob, String> {
}
