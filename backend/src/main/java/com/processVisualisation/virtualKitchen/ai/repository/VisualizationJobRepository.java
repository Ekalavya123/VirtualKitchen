package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationJob;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationJobStatus;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link VisualizationJob}, persisting the
 * progress and results of asynchronous recipe-visualization runs in the
 * {@code visualization_job} collection.
 */
public interface VisualizationJobRepository extends MongoRepository<VisualizationJob, String> {

    /** The job currently running for a process, if any (see {@link VisualizationJob#getActiveKey()}). */
    Optional<VisualizationJob> findByActiveKey(String activeKey);

    /** Every job for a recipe in one of the given statuses — used to rediscover running jobs after a reload. */
    List<VisualizationJob> findByRecipeIdAndStatusIn(String recipeId, Collection<VisualizationJobStatus> statuses);
}
