package com.processVisualisation.virtualKitchen.ai.workflow.model;

/**
 * Lifecycle of one task inside a {@link RecipeAiWorkflow}, as reported to the UI. Never stored:
 * it is derived on every read from the workflow's gate state and the underlying jobs (generation
 * job, visualization jobs, step narration records), so it can't drift from the work itself.
 */
public enum RecipeAiTaskStatus {
    /** Selected, but an earlier stage hasn't finished yet (e.g. narration while the process is generating). */
    PENDING,
    QUEUED,
    RUNNING,
    /** Downstream task held until the user approves the recipe process. */
    WAITING_FOR_APPROVAL,
    COMPLETED,
    /** At least one item failed; {@code failed} on the task says how many, and only those are retried. */
    FAILED,
    /** Not selected, or (PROCESS) the workflow works on the recipe's existing process. */
    SKIPPED,
    CANCELLED
}
