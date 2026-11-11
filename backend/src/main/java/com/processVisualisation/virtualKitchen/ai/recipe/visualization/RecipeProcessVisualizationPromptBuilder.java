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
            prompt.append(storyboardInstruction(output, steps.size())).append("\n\n");
            prompt.append("Generate imagePrompt: a single image describing that storyboard.");
        }
        return prompt.toString();
    }

    /**
     * Bump whenever the batch system/user prompt wording or structure changes, so a change in image quality can be
     * tied to a prompt change (logged with every batch).
     */
    public static final String BATCH_PROMPT_VERSION = "3";

    /** One image the batch call writes a prompt for, under the short id the model answers with ("m1", "p1"). */
    public record BatchTarget(String id, RecipeProcessVisualizationService.VisualTarget target) {
    }

    /**
     * System prompt for writing every image prompt of a recipe in one call: one shared style guide first, then one
     * self-contained prompt per target, so the images read as one recipe shot in one kitchen.
     */
    public String buildBatchSystemPrompt() {
        return """
                You are a culinary visualization prompt writer. You write the image prompts for every image of one \
                recipe at once, so all images look like one cooking session.

                Return strict JSON only: {"styleGuide": "...", "dishContext": "...", "scenePrompt": "...", \
                "prompts": [{"id": "...", "imagePrompt": "..."}]}.
                No markdown, prose, or code fences.

                - First write styleGuide: one short paragraph fixing the shared look of every image: the kitchen and \
                countertop, the cookware (name each pan or pot used), the lighting, the camera angle, and the \
                photographic style (photorealistic).
                - When asked for a SCENE, also write:
                  - dishContext: one line naming the dish, its cuisine and style (e.g. "Part of making Chicken \
                Curry, a North Indian home-style dish"). No steps, ingredients or quantities.
                  - scenePrompt: an image prompt for the EMPTY setting every image is drawn in: the countertop, the \
                main pan or pot of the main cooking steps on the stove, a cutting board and empty prep bowls, the \
                lighting and the camera angle from the styleGuide. Absolutely no food and no ingredients.
                  Otherwise omit both.
                - Then write exactly one prompt per listed id, using that id.
                - Read the whole recipe before writing. For each MAIN step, use the earlier steps to work out what \
                is already in the pan, then describe that state explicitly in this step's prompt (e.g. "golden-brown \
                fried onions with chopped tomatoes just added").
                - Show only the current state after this step: ingredients that were cooked down have changed form, \
                and nothing from earlier is shown separately unless it is still there.
                - Each Preparation is its own flow: do not carry state between preparations or from the main steps. \
                A MAIN step that uses a prepared component shows it by name.
                - Each imagePrompt is sent to the image model ON ITS OWN: it must be self-contained, so repeat the \
                relevant parts of the style guide (same kitchen, same cookware, same lighting) inside every prompt. \
                Never write "as before", "same as the previous step", "the previous image", or another id.
                - Show each step's action and its expected result; keep quantities and ingredients faithful to the step.
                - Keep the same cookware for the same pan across MAIN steps: the food in it changes, the pan does not.
                - No text, labels, captions or watermarks in any image.
                """;
    }

    /**
     * The whole recipe, in cooking order: MAIN steps as {@code m<i>} (each with its previous step's result for
     * continuity) and Preparation flows as {@code p<i>} (one storyboard image per flow, as in
     * {@link #buildFlowUserPrompt}). {@code styleReferences} are prompts this recipe already has, which new prompts
     * must match.
     */
    public String buildBatchUserPrompt(List<BatchTarget> targets, List<String> styleReferences) {
        return buildBatchUserPrompt(targets, styleReferences, null, false);
    }

    /**
     * Same as {@link #buildBatchUserPrompt(List, List)}, also naming the dish and, when {@code sceneNeeded}, asking
     * for the recipe's dishContext and empty-scene prompt (its scene anchor).
     */
    public String buildBatchUserPrompt(List<BatchTarget> targets, List<String> styleReferences, String dishName, boolean sceneNeeded) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(dishName)) {
            prompt.append("DISH: ").append(dishName.trim()).append("\n\n");
        }
        List<BatchTarget> flows = targets.stream().filter(target -> target.target().flow() != null).toList();
        List<BatchTarget> steps = targets.stream().filter(target -> target.target().flow() == null).toList();

        if (!flows.isEmpty()) {
            prompt.append("PREPARATIONS (one image each, made before or alongside cooking):\n");
            for (BatchTarget target : flows) {
                RecipeProcessVisualizationService.FlowContext flow = target.target().flow();
                String output = StringUtils.hasText(flow.outputName()) ? flow.outputName().trim() : "the prepared component";
                prompt.append(target.id()).append(": making ").append(output).append('\n');
                for (int i = 0; i < flow.steps().size(); i++) {
                    prompt.append("  ").append(i + 1).append(". ").append(describeStep(flow.steps().get(i))).append('\n');
                }
                prompt.append("  Image: ").append(flow.steps().size() <= 1
                        ? "a single still frame showing this preparation and the finished " + output + "."
                        : storyboardInstruction(output, flow.steps().size())).append('\n');
            }
            prompt.append('\n');
        }

        if (!steps.isEmpty()) {
            prompt.append("MAIN COOKING STEPS (one image each, a single still frame of the step and its result):\n");
            for (BatchTarget target : steps) {
                RecipeProcessVisualizationInput step = target.target().step();
                prompt.append(target.id()).append(": ").append(describeStep(step)).append('\n');
                if (StringUtils.hasText(step.previousExpectedOutput())) {
                    prompt.append("  Continues from: ").append(step.previousExpectedOutput()).append('\n');
                }
                if (step.ingredients().size() > 1) {
                    prompt.append("  Several ingredients at once: show them together in one combined scene.\n");
                }
            }
            prompt.append('\n');
        }

        if (styleReferences != null && !styleReferences.isEmpty()) {
            prompt.append("EXISTING PROMPTS OF THIS RECIPE (match their kitchen, cookware, lighting and style exactly):\n");
            styleReferences.forEach(reference -> prompt.append("- ").append(reference).append('\n'));
            prompt.append('\n');
        }

        if (sceneNeeded) {
            prompt.append("SCENE: also write dishContext and scenePrompt for this recipe.\n\n");
        }
        if (targets.isEmpty()) {
            prompt.append("Return the styleGuide, dishContext and scenePrompt; prompts is an empty list.");
        } else {
            prompt.append("Return the styleGuide and one prompt for each of these ids: ")
                    .append(targets.stream().map(BatchTarget::id).collect(java.util.stream.Collectors.joining(", ")))
                    .append('.');
        }
        return prompt.toString();
    }

    /** Asks the image model to keep the reference image's setting and change only the food and the step's action. */
    static final String REFERENCE_INSTRUCTION = "Use the kitchen, countertop, cookware and lighting shown in the "
            + "reference image; change only the food and what happens in this step.";

    /**
     * The text sent to the image model for one step: the dish context line, then (when the scene anchor is sent as a
     * reference image) how to use it, then the step's own prompt.
     */
    public String buildImageRequestText(String imagePrompt, String dishContext, boolean hasReference) {
        StringBuilder text = new StringBuilder();
        if (StringUtils.hasText(dishContext)) text.append(dishContext.trim()).append('\n');
        if (hasReference) text.append(REFERENCE_INSTRUCTION).append('\n');
        return text.append(imagePrompt).toString();
    }

    // ---------------------------------------------------------------------------------------------------------
    // Focused Cooking View: the text model describes only the food and the action; the scene (equipment, camera,
    // lighting, style) and the framing are written here, identically for every image of a recipe.
    // ---------------------------------------------------------------------------------------------------------

    /** Version of the focused batch prompt; logged with every batch like {@link #BATCH_PROMPT_VERSION}. */
    public static final String FOCUSED_BATCH_PROMPT_VERSION = "4";

    /** System prompt of the focused batch call: one {@link ActionContext} per image, and the recipe's equipment. */
    public String buildFocusedBatchSystemPrompt() {
        return """
                You are a culinary visualization writer. You describe every image of one recipe at once. The kitchen, \
                cookware, camera, lighting and photographic style are added to each image by the system, so you never \
                describe them: you describe only the food and the action.

                Return strict JSON only: {"dishContext": "...", "scene": {"vessel": "...", "stove": "...", \
                "surface": "...", "cuttingBoard": "...", "prepBowl": "...", "servingDish": "..."}, \
                "prompts": [{"id": "...", "focus": "...", "state": "...", "action": "..."}]}.
                No markdown, prose, or code fences.

                - When asked for a SCENE, also write:
                  - dishContext: one line naming the dish, its cuisine and style (e.g. "Part of making Chicken \
                Curry, a North Indian home-style dish"). No steps, ingredients or quantities.
                  - scene: the equipment this recipe is cooked with, each a short noun phrase with its article, colour \
                and material (e.g. "a black round frying pan", "a black gas stove", "a light grey stone countertop", \
                "a wooden cutting board", "a plain steel mixing bowl", "a plain white plate"). vessel is the ONE main \
                pan or pot of the main cooking steps: pick what the recipe needs (frying pan, kadai, saucepan, pot). \
                Keep everything plain: no brands, no decoration.
                  Otherwise omit both.
                - Write exactly one entry per listed id, using that id.
                - focus: where the camera looks. CUTTING_BOARD (cutting, peeling, cleaning on the board), \
                COOKING_VESSEL (anything in the main pan or pot), PREP_BOWL (mixing, marinating or coating in a bowl), \
                PLATE (plating, serving). Each id lists its default focus; keep it unless the step clearly happens \
                somewhere else.
                - state: what is visible in the focus at this step, in one or two sentences. Read the whole recipe \
                first. For a COOKING_VESSEL step, work out from the earlier MAIN steps everything already in the \
                vessel and describe it in its current form (e.g. "softened translucent chopped onions in shimmering \
                oil"): every ingredient added earlier is still there unless a step removed it, and it changes form as \
                it cooks. Then name what this step adds, in its prepared form and visible amount.
                - action: what is being done right now, with the utensil or hand doing it (e.g. "chopped red tomatoes \
                being tipped in from a small bowl", "a wooden spatula stirring the mixture"). For a step that only \
                cooks or waits, describe what the cooking looks like (bubbling, steam, colour).
                - Never show the future: no ingredient a later step adds, no finished dish before the last step, \
                nothing cooked further than this step's expected result.
                - Never show extras: only the ingredients and utensils this step needs.
                - Name ingredients by what they look like (colour, cut, raw or cooked) and keep quantities faithful to \
                the step.
                - Do not describe the kitchen, cookware, stove, countertop, camera, lighting or style; refer to the \
                equipment only as "the pan", "the pot", "the board", "the bowl" or "the plate". Never write "as \
                before", "the previous image" or another id: each entry must read on its own.
                - Each Preparation is its own flow: do not carry state between preparations or from the main steps. \
                A MAIN step that uses a prepared component shows it by name.
                - A Preparation listed with focus STORYBOARD: state lists its panels in order, one short clause each, \
                ending with the finished component; action stays empty.
                - No text, labels, captions or watermarks.
                """;
    }

    /**
     * The whole recipe for the focused batch call, as in {@link #buildBatchUserPrompt}, with each image's default
     * focus.
     *
     * @param focusById the default {@link VisualFocus} of every target id
     * @param sceneNeeded whether to ask for the recipe's dishContext and equipment
     */
    public String buildFocusedBatchUserPrompt(List<BatchTarget> targets, java.util.Map<String, VisualFocus> focusById,
                                              String dishName, boolean sceneNeeded) {
        StringBuilder prompt = new StringBuilder();
        if (StringUtils.hasText(dishName)) {
            prompt.append("DISH: ").append(dishName.trim()).append("\n\n");
        }
        List<BatchTarget> flows = targets.stream().filter(target -> target.target().flow() != null).toList();
        List<BatchTarget> steps = targets.stream().filter(target -> target.target().flow() == null).toList();

        if (!flows.isEmpty()) {
            prompt.append("PREPARATIONS (one image each, made before or alongside cooking):\n");
            for (BatchTarget target : flows) {
                RecipeProcessVisualizationService.FlowContext flow = target.target().flow();
                String output = StringUtils.hasText(flow.outputName()) ? flow.outputName().trim() : "the prepared component";
                prompt.append(target.id()).append(": making ").append(output).append('\n');
                for (int i = 0; i < flow.steps().size(); i++) {
                    prompt.append("  ").append(i + 1).append(". ").append(describeStep(flow.steps().get(i))).append('\n');
                }
                VisualFocus focus = focusById.get(target.id());
                if (focus == VisualFocus.STORYBOARD) {
                    prompt.append("  Image: ").append(storyboardInstruction(output, flow.steps().size())).append('\n');
                    prompt.append("  Focus: STORYBOARD\n");
                } else {
                    prompt.append("  Image: a single close-up still frame of this preparation and the finished ")
                            .append(output).append(".\n");
                    prompt.append("  Default focus: ").append(focus).append('\n');
                }
            }
            prompt.append('\n');
        }

        if (!steps.isEmpty()) {
            prompt.append("MAIN COOKING STEPS (one image each, a single close-up still frame of the step):\n");
            for (BatchTarget target : steps) {
                RecipeProcessVisualizationInput step = target.target().step();
                prompt.append(target.id()).append(": ").append(describeStep(step)).append('\n');
                if (StringUtils.hasText(step.previousExpectedOutput())) {
                    prompt.append("  Continues from: ").append(step.previousExpectedOutput()).append('\n');
                }
                prompt.append("  Default focus: ").append(focusById.get(target.id())).append('\n');
            }
            prompt.append('\n');
        }

        if (sceneNeeded) {
            prompt.append("SCENE: also write dishContext and scene for this recipe.\n\n");
        }
        if (targets.isEmpty()) {
            prompt.append("Return dishContext and scene; prompts is an empty list.");
        } else {
            prompt.append("Return one entry for each of these ids: ")
                    .append(targets.stream().map(BatchTarget::id).collect(java.util.stream.Collectors.joining(", ")))
                    .append('.');
        }
        return prompt.toString();
    }

    /** Added to the per-step fallback prompts when the focused view is on: the answer is the step's own text only. */
    static final String FOCUSED_FALLBACK_INSTRUCTION = "\n\nThe kitchen, cookware, camera, lighting and style are "
            + "added by the system: describe ONLY the food that is visible at this step and what is being done to it, "
            + "with the utensil or hand doing it. Show nothing a later step adds and nothing the step does not need.";

    /** {@link #buildUserPrompt} for a focused image: the same step, answered with the food and the action only. */
    public String buildFocusedUserPrompt(RecipeProcessVisualizationInput current) {
        return buildUserPrompt(current) + FOCUSED_FALLBACK_INSTRUCTION;
    }

    /** {@link #buildFlowUserPrompt} for a focused image. */
    public String buildFocusedFlowUserPrompt(String outputName, List<RecipeProcessVisualizationInput> steps) {
        return buildFlowUserPrompt(outputName, steps) + FOCUSED_FALLBACK_INSTRUCTION;
    }

    /** Which reference images go with a focused image request. */
    public record References(boolean sceneAnchor, boolean previousStep) {

        public static final References NONE = new References(false, false);

        public int count() {
            return (sceneAnchor ? 1 : 0) + (previousStep ? 1 : 0);
        }
    }

    static final String ANCHOR_AND_PREVIOUS_INSTRUCTION = "The first reference image is the empty setup and the second "
            + "is the previous step. Continue from the second image: keep the same cookware, stove, countertop, "
            + "lighting and camera, and change only what this step does.";

    static final String PREVIOUS_INSTRUCTION = "The reference image is the previous step. Continue from it: keep the "
            + "same equipment, surface, lighting and camera, and change only what this step does.";

    static final String FOCUSED_GUARDRAIL = "Show only what is described: no other ingredients, dishes or utensils, "
            + "no text or labels.";

    /**
     * The text sent to the image model for one focused image, assembled from fixed parts so every image of a recipe
     * describes its scene in the same words: camera and framing (from the focus and the recipe's equipment), the
     * step's own text, lighting and style, the guardrail, the dish context, then how to use the reference images.
     *
     * @param stepText the asset's stored prompt: what is in the frame and what is being done
     * @param scene the recipe's equipment, already completed with the configured defaults
     */
    public String buildFocusedImageRequestText(String stepText, VisualFocus focus, SceneContext scene,
                                               VisualizationImageProperties.Scene style, String dishContext,
                                               References references) {
        StringBuilder text = new StringBuilder();
        text.append(cameraLine(focus, style)).append(' ').append(focus.framing(scene)).append('\n');
        text.append(stepText.trim()).append('\n');
        text.append(styleLine(style)).append('\n');
        text.append(FOCUSED_GUARDRAIL);
        if (StringUtils.hasText(dishContext)) text.append("\nContext: ").append(dishContext.trim());
        if (references.sceneAnchor() && references.previousStep()) {
            text.append('\n').append(ANCHOR_AND_PREVIOUS_INSTRUCTION);
        } else if (references.previousStep()) {
            text.append('\n').append(PREVIOUS_INSTRUCTION);
        } else if (references.sceneAnchor()) {
            text.append('\n').append(REFERENCE_INSTRUCTION);
        }
        return text.toString();
    }

    /**
     * The prompt of a recipe's scene anchor for focused images: the empty main vessel on the stove, framed exactly
     * like a cooking step, so it can be the reference every cooking step is drawn against.
     */
    public String buildFocusedScenePrompt(SceneContext scene, VisualizationImageProperties.Scene style) {
        return cameraLine(VisualFocus.COOKING_VESSEL, style) + ' ' + VisualFocus.COOKING_VESSEL.framing(scene) + '\n'
                + "The cookware is empty and clean: absolutely no food, no ingredients, no utensils, no hands.\n"
                + styleLine(style) + '\n'
                + "No text or labels.";
    }

    private static String cameraLine(VisualFocus focus, VisualizationImageProperties.Scene style) {
        String camera = "camera about " + style.getCameraAngle() + " degrees above the counter looking down";
        return focus == VisualFocus.STORYBOARD
                ? "One image laid out as a storyboard of small close-up panels in a clean grid, read left to right and "
                + "top to bottom, " + camera + " in every panel."
                : "Close-up photo, " + camera + ".";
    }

    private static String styleLine(VisualizationImageProperties.Scene style) {
        String line = style.getLighting() + ", " + style.getStyle() + ", " + style.getEnvironment() + ".";
        return Character.toUpperCase(line.charAt(0)) + line.substring(1);
    }

    /** The storyboard layout for a multi-step preparation flow's single image. */
    private static String storyboardInstruction(String output, int stepCount) {
        int panels = Math.min(stepCount, MAX_FLOW_PANELS) + 1;
        StringBuilder instruction = new StringBuilder("Compose ONE image laid out as a storyboard of ").append(panels)
                .append(" small panels in a clean grid, read left to right and top to bottom: one panel per step in ")
                .append("the order above");
        if (stepCount > MAX_FLOW_PANELS) instruction.append(" (merge consecutive minor steps to fit)");
        instruction.append(", and a final panel showing the finished ").append(output)
                .append(". Keep the same kitchen, lighting and ingredients across panels; no text or labels.");
        return instruction.toString();
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
