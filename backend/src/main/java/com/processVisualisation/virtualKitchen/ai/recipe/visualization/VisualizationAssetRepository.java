package com.processVisualisation.virtualKitchen.ai.recipe.visualization;

import com.processVisualisation.virtualKitchen.ai.recipe.visualization.model.VisualizationAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link VisualizationAsset}, persisting
 * generated visualization assets (images/videos) in the
 * {@code visualization_asset} collection.
 */
public interface VisualizationAssetRepository extends MongoRepository<VisualizationAsset, Long> {
    /** Finds the visualization asset previously generated for the given cache/dedup key, if any. */
    Optional<VisualizationAsset> findByVisualizationKey(String visualizationKey);
}
