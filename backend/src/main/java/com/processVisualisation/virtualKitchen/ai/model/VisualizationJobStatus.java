package com.processVisualisation.virtualKitchen.ai.model;

/**
 * Lifecycle states of an asynchronous {@link VisualizationJob}, from being
 * queued through completion (with or without per-step errors) or failure.
 */
public enum VisualizationJobStatus {
    /** Job has been created and is waiting to start processing. */
    QUEUED,
    /** Job is actively processing steps. */
    IN_PROGRESS,
    /** All steps completed successfully. */
    COMPLETED,
    /** All steps finished but one or more individual steps failed. */
    COMPLETED_WITH_ERRORS,
    /** The job failed and did not complete. */
    FAILED
}
