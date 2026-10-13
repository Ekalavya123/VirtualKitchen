package com.processVisualisation.virtualKitchen.recipe.service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Finds the generated images that recipe steps reference by asset id. Implemented by the AI visualization
 * feature, which owns those assets, so the recipe feature can show them without depending on it.
 */
public interface StepImageLookup {

    /**
     * A generated step image.
     *
     * @param imageUrl    the stored image's URL, or null when none was stored
     * @param failed      whether generating the image failed
     * @param generatedAt when the image was last (re)generated
     */
    record StepImage(Long assetId, String imageUrl, boolean failed, LocalDateTime generatedAt) {
    }

    /** The images that exist among {@code assetIds}, keyed by asset id; unknown ids are left out. */
    Map<Long, StepImage> findByIds(Collection<Long> assetIds);
}
