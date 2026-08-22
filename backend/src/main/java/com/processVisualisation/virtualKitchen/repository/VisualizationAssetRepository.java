package com.processVisualisation.virtualKitchen.repository;

import com.processVisualisation.virtualKitchen.model.VisualizationAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VisualizationAssetRepository extends MongoRepository<VisualizationAsset, Long> {
    Optional<VisualizationAsset> findByVisualizationKey(String visualizationKey);
}
