package com.processVisualisation.virtualKitchen.ai.recipe.visualization;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Builds AI prompts for recipe STEP visualization (image only — no video in V1) from
 * {@link RecipeProcessVisualizationInput}: a step's Action On ingredients and subprocess
 * references.
 * <p>
 * Contract: exactly one image per step. When a step targets more than one ingredient (or
 * simultaneous operations), the prompt explicitly asks for a single composite/side-by-side image
 * rather than one image per ingredient.
 */
@Component
public class RecipeProcessVisualizationPromptBuilder {

    public String buildSystemPrompt() {
        return "You are a culinary visualization prompt writer. "
                + "Return strict JSON only: {\"imagePrompt\": \"...\"}. "
                + "No markdown, prose, or code fences.";
    }

    public String buildUserPrompt(RecipeProcessVisualizationInput current) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Describe a photorealistic kitchen visualization for the current cooking step.\n\n");
        prompt.append("Current step:\n").append(describeStep(current)).append("\n\n");

        if (StringUtils.hasText(current.previousExpectedOutput())) {
            prompt.append("Previous step's expected result (for visual continuity - the current step "
                            + "continues from this state, same kitchen and camera setup):\n")
                    .append(current.previousExpectedOutput()).append("\n\n");
        } else {
            prompt.append("This is the first step of the process.\n\n");
        }

        if (current.ingredients().size() > 1) {
            prompt.append("This step involves multiple simultaneous ingredient operations. Compose a single "
                    + "image that clearly shows all of them together — for example a side-by-side or clearly "
                    + "labeled/visually distinguishable composition within the same frame. Do not describe "
                    + "separate images, only one combined scene.\n\n");
        }

        prompt.append("Generate imagePrompt: a single still frame depicting this step and its expected result.");
        return prompt.toString();
    }

    /** At most this many panels in one preparation image; longer flows merge their middle steps. */
    static final int MAX_FLOW_PANELS = 6;

    /**
     * One image for a whole independent preparation flow (e.g. "Marinated Chicken"), attached to the step that makes
     * its output. A single step is one scene; several steps become one image laid out as a storyboard of small
     * panels in cooking order, ending with the finished component.
     */
    public String buildFlowUserPrompt(String outputName, List<RecipeProcessVisualizationInput> steps) {
        String output = StringUtils.hasText(outputName) ? outputName.trim() : "the prepared component";
        StringBuilder prompt = new StringBuilder();
        prompt.append("Describe a photorealistic kitchen visualization of one complete preparation: making ")
                .append(output).append(".\n\n");
        prompt.append("Preparation steps, in order:\n");
        for (int i = 0; i < steps.size(); i++) {
            prompt.append(i + 1).append(". ").append(describeStep(steps.get(i))).append('\n');
        }
        prompt.append('\n');
        if (steps.size() <= 1) {
            prompt.append("Generate imagePrompt: a single still frame showing this preparation and the finished ")
                    .append(output).append(".");
        } else {
            int panels = Math.min(steps.size(), MAX_FLOW_PANELS) + 1;
            prompt.append("Compose ONE image laid out as a storyboard of ").append(panels)
                    .append(" small panels in a clean grid, read left to right and top to bottom: one panel per step in ")
                    .append("the order above");
            if (steps.size() > MAX_FLOW_PANELS) prompt.append(" (merge consecutive minor steps to fit)");
            prompt.append(", and a final panel showing the finished ").append(output)
                    .append(". Keep the same kitchen, lighting and ingredients across panels; no text or labels.\n\n");
            prompt.append("Generate imagePrompt: a single image describing that storyboard.");
        }
        return prompt.toString();
    }

    private String describeStep(RecipeProcessVisualizationInput step) {
        StringBuilder description = new StringBuilder();
        description.append("action=").append(orNone(step.action()));

        if (!step.ingredients().isEmpty()) {
            description.append(", actionOn.ingredients=[");
            for (int i = 0; i < step.ingredients().size(); i++) {
                RecipeProcessVisualizationInput.IngredientTarget ingredient = step.ingredients().get(i);
                if (i > 0) description.append("; ");
                description.append(ingredient.name());
                if (StringUtils.hasText(ingredient.quantity())) {
                    description.append(" (").append(ingredient.quantity());
                    if (StringUtils.hasText(ingredient.unit())) description.append(' ').append(ingredient.unit());
                    description.append(')');
                }
                if (StringUtils.hasText(ingredient.preparationStyle())) {
                    description.append(" - ").append(ingredient.preparationStyle());
                }
            }
            description.append(']');
        }

        if (!step.subprocessNames().isEmpty()) {
            description.append(", actionOn.subprocesses=").append(String.join(", ", step.subprocessNames()));
        }

        description.append(", actionDescription=").append(orNone(step.actionDescription()));
        description.append(", expectedOutput=").append(orNone(step.expectedOutput()));

        List<String> advanced = advancedProperties(step);
        if (!advanced.isEmpty()) {
            description.append(", advanced=").append(String.join(", ", advanced));
        }

        return description.toString();
    }

    private List<String> advancedProperties(RecipeProcessVisualizationInput step) {
        return java.util.stream.Stream.of(
                        StringUtils.hasText(step.temperature()) ? "temperature=" + step.temperature() : null,
                        StringUtils.hasText(step.flameLevel()) ? "flame=" + step.flameLevel() : null,
                        StringUtils.hasText(step.duration()) ? "duration=" + step.duration() : null)
                .filter(StringUtils::hasText)
                .toList();
    }

    private String orNone(String value) {
        return StringUtils.hasText(value) ? value : "(none)";
    }
}
