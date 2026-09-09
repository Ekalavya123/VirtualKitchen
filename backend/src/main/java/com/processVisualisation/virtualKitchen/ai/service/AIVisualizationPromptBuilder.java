package com.processVisualisation.virtualKitchen.ai.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds AI prompts for recipe-step visualization (image + video) with
 * continuity context from the previous step, so future frames can stay consistent.
 */
@Component
public class AIVisualizationPromptBuilder {

    /**
     * Builds the fixed system prompt instructing the AI model to act as a
     * culinary visualization prompt writer and to respond with strict JSON.
     *
     * @return the system prompt text
     */
    public String buildSystemPrompt() {
        return "You are a culinary visualization prompt writer. "
                + "Return strict JSON only: {\"imagePrompt\": \"...\", \"videoPrompt\": \"...\"}. "
                + "No markdown, prose, or code fences.";
    }

    /**
     * Builds the user prompt describing the current recipe step (and, when
     * available, the previous step for visual continuity) that the AI model
     * should turn into image/video generation prompts.
     *
     * @param currentStep the step data to visualize
     * @param previousStep the preceding step's data, or {@code null} if this is the first step
     * @return the composed user prompt text
     */
    public String buildUserPrompt(Map<String, Object> currentStep, Map<String, Object> previousStep) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Describe a photorealistic kitchen visualization for the current cooking step.\n\n");
        prompt.append("Current step:\n").append(describeStep(currentStep)).append("\n\n");

        if (previousStep != null) {
            prompt.append("Previous step (for visual continuity - same kitchen, ingredients and camera setup):\n")
                    .append(describeStep(previousStep)).append("\n\n");
        } else {
            prompt.append("This is the first step of the recipe.\n\n");
        }

        prompt.append("Generate:\n")
                .append("- imagePrompt: a single still frame depicting this step.\n")
                .append("- videoPrompt: a short clip prompt showing the action happening, continuing naturally from the previous step.");
        return prompt.toString();
    }

    private String describeStep(Map<String, Object> step) {
        if (step == null) {
            return "(none)";
        }
        return "action=" + valueOf(step, "action")
                + ", ingredient=" + firstNonBlank(step, "ingredientId", "customIngredientName")
                + ", preparationStyle=" + firstNonBlank(step, "preparationStyle", "customPreparationStyle", "preparationStyleId")
                + ", flame=" + firstNonBlank(step, "flameLevel", "customFlameLevel", "flameLevelId")
                + ", temperature=" + valueOf(step, "temperature")
                + ", duration=" + valueOf(step, "duration")
                + ", notes=" + valueOf(step, "notes");
    }

    private String firstNonBlank(Map<String, Object> step, String... keys) {
        for (String key : keys) {
            String value = valueOf(step, key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private String valueOf(Map<String, Object> step, String key) {
        Object value = step.get(key);
        return value == null ? "" : String.valueOf(value);
    }
}
