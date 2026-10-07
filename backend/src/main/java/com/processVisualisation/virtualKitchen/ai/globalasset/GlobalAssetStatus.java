package com.processVisualisation.virtualKitchen.ai.globalasset;

/** Lifecycle of a global resource's image generation, stored on its {@code VisualizationAsset}. */
public enum GlobalAssetStatus {
    /** Claimed and waiting for a worker on the global-asset pool. */
    QUEUED,
    /** A worker is generating or uploading the image. */
    GENERATING,
    /** The image is stored and published to the catalog resource. */
    READY,
    /** The last generation failed; a new request may retry it. */
    FAILED
}
