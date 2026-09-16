package com.processVisualisation.virtualKitchen.ai.model;

/**
 * Internal milestones of a single {@link RecipeFlowGenerationJob} run. Since
 * flow generation is one (occasionally two) LLM call rather than many
 * independently-completable steps, progress is reported as these coarse,
 * real stage transitions rather than a "completed/total" count. Each stage
 * carries a fixed percent-complete used to populate
 * {@code RecipeFlowGenerationJobResponseDTO#progressPercent} — the single
 * source of truth for the mapping, kept out of the frontend.
 */
public enum RecipeFlowGenerationStage {
    QUEUED(0),
    BUILDING_PROMPT(10),
    CALLING_MODEL(35),
    VALIDATING_RESPONSE(65),
    /** Only reached if the first attempt fails validation and a retry is made. */
    RETRYING(80),
    PERSISTING(95),
    COMPLETED(100);

    private final int percent;

    RecipeFlowGenerationStage(int percent) {
        this.percent = percent;
    }

    public int getPercent() {
        return percent;
    }
}
