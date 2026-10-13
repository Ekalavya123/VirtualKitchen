package com.processVisualisation.virtualKitchen.ai.recipe.visualization;

import com.processVisualisation.virtualKitchen.ai.asset.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.asset.VisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.recipe.service.StepImageLookup;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Serves recipe step images from the visualization assets the visualization jobs generate. */
@Component
public class VisualizationStepImageLookup implements StepImageLookup {

    private final VisualizationAssetRepository assetRepository;

    public VisualizationStepImageLookup(VisualizationAssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @Override
    public Map<Long, StepImage> findByIds(Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        return assetRepository.findAllById(assetIds).stream()
                .map(VisualizationStepImageLookup::toStepImage)
                .collect(Collectors.toMap(StepImage::assetId, Function.identity(), (a, b) -> a));
    }

    private static StepImage toStepImage(VisualizationAsset asset) {
        return new StepImage(
                asset.getId(),
                asset.getImageUrl(),
                asset.getImageFailureReason() != null,
                asset.getUpdatedAt() != null ? asset.getUpdatedAt() : asset.getCreatedAt());
    }
}
