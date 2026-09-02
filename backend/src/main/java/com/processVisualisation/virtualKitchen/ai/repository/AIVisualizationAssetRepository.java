package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface AIVisualizationAssetRepository extends MongoRepository<VisualizationAsset, Long> {
    Optional<VisualizationAsset> findByVisualizationKey(String visualizationKey);
}
