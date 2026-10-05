package com.processVisualisation.virtualKitchen.ai.workflow.model;

/**
 * What an AI Recipe Creation workflow can be asked to create. Each type is carried out by its own
 * existing service (process generation, visualization, narration); the workflow only orders them.
 * <p>
 * Declaration order is the display order and the dependency order: VISUALS and NARRATION depend on
 * an approved PROCESS, never on each other.
 */
public enum RecipeAiTaskType {
    PROCESS,
    VISUALS,
    NARRATION;

    /** True for the tasks that may only start once the user has approved the recipe process. */
    public boolean isDownstream() {
        return this != PROCESS;
    }
}
