package com.processVisualisation.virtualKitchen.ai.registry;

/**
 * Classifies an AI model/provider by cost tier. Deliberately an extensible
 * enum rather than a boolean so additional tiers (e.g. an enterprise or
 * bring-your-own-key tier) can be introduced later without changing any
 * selection, credit, or queue logic that switches on it.
 */
public enum ModelTier {
    PAID,
    OPEN_SOURCE
}
