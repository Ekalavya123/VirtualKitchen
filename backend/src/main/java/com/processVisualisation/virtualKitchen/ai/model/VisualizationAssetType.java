package com.processVisualisation.virtualKitchen.ai.model;

/**
 * Classifies a {@link VisualizationAsset} as either a single generated
 * unit or one composed from other assets.
 */
public enum VisualizationAssetType {
    /** A standalone, individually generated visualization asset. */
    ATOMIC,
    /** An asset composed/assembled from multiple atomic assets. */
    COMPOSITE
}
