package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.asset.GlobalResourceType;

/**
 * The catalog facts about a global resource that image generation needs, independent of which
 * catalog (ingredient, equipment) it came from.
 *
 * @param imageUrl the image currently published on the catalog entry, or null
 */
public record GlobalResource(
        GlobalResourceType type,
        Long id,
        String name,
        String description,
        String imageUrl
) {
}
