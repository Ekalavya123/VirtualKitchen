package com.processVisualisation.virtualKitchen.ai.model;

/**
 * Internal milestones of a single {@link RecipeProcessGenerationJob} run. There is no
 * PERSISTING stage here: the
 * generated result is never written to the database as part of this
 * pipeline — it is only loaded into the frontend's Recipe working session,
 * so the job simply completes once validation succeeds.
 */
public enum RecipeProcessGenerationStage {
    QUEUED(0),
    BUILDING_PROMPT(10),
    CALLING_MODEL(40),
    VALIDATING_RESPONSE(75),
    /** Only reached if the first attempt fails validation and a retry is made. */
    RETRYING(90),
    COMPLETED(100);

    private final int percent;

    RecipeProcessGenerationStage(int percent) {
        this.percent = percent;
    }

    public int getPercent() {
        return percent;
    }
}
