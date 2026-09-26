package com.processVisualisation.virtualKitchen.ai.model;

/**
 * Lifecycle states of an asynchronous {@link RecipeProcessGenerationJob}, from
 * being queued through completion or failure.
 */
public enum RecipeProcessGenerationJobStatus {
    /** Job has been created and is waiting to start processing. */
    QUEUED,
    /** Job is actively generating/validating the recipe flow. */
    IN_PROGRESS,
    /** The flow was generated and validated successfully. */
    COMPLETED,
    /** The job failed and did not produce a usable flow. */
    FAILED
}
