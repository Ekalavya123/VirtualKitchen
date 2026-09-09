package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationJob;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VisualizationJobRepository extends MongoRepository<VisualizationJob, String> {
}
