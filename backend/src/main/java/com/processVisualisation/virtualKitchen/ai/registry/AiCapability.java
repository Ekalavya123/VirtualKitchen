package com.processVisualisation.virtualKitchen.ai.registry;

/**
 * The kind of AI operation a {@link ModelDefinition} serves. Kept separate
 * from {@link ModelTier} so a future capability (e.g. text-to-speech) can be
 * added without touching the tier classification.
 */
public enum AiCapability {
    TEXT_TO_TEXT,
    TEXT_TO_IMAGE
}
