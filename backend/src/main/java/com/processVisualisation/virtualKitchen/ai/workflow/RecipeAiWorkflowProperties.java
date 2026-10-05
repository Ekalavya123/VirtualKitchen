package com.processVisualisation.virtualKitchen.ai.workflow;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds {@code app.ai-workflow.*}: how AI Recipe Creation weighs its tasks for the overall progress
 * figure, and the step range it assumes when estimating credits before the process exists.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai-workflow")
public class RecipeAiWorkflowProperties {

    private Weights weights = new Weights();
    private Estimate estimate = new Estimate();

    /**
     * Relative share of each task in the overall percentage (roughly its share of the wait): one
     * text call for the process, a prompt plus an image per step for visuals, one TTS call per step
     * for narration. Only selected, non-skipped tasks count; the weights are normalised over those.
     */
    @Data
    public static class Weights {
        private int process = 2;
        private int visuals = 5;
        private int narration = 3;
    }

    /** Step range assumed for a recipe whose process hasn't been generated yet. */
    @Data
    public static class Estimate {
        private int minSteps = 6;
        private int maxSteps = 20;
    }
}
