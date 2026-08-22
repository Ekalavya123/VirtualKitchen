package com.processVisualisation.virtualKitchen.service.recipe;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Builds AI prompts for recipe-step visualization (image + video) with
 * continuity context from the previous step, so future frames can stay consistent.
 */
@Component
public class VisualizationPromptBuilder {

    public String buildSystemPrompt() {
        return "You are a culinary visualization prompt writer. "
                + "Return strict JSON only: {\"imagePrompt\": \"...\", \"videoPrompt\": \"...\"}. "
                + "No markdown, prose, or code fences.";
    }

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
