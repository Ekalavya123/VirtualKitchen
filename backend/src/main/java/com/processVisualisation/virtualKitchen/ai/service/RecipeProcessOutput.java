package com.processVisualisation.virtualKitchen.ai.service;

import java.util.List;

/**
 * The compact JSON the model returns for recipe text -> Process generation, exactly as described by
 * {@link RecipeProcessOutputSchema}. Optional fields are simply absent (null here);
 * {@link RecipeProcessOutputNormalizer} fills the defaults while mapping into the {@code Generated*DTO}s.
 */
public record RecipeProcessOutput(Process mainProcess, List<Process> subprocesses) {

    /** MAIN process (no ref) or a SUBPROCESS. */
    public record Process(String ref, String name, List<Node> steps) {}

    /** A STEP (nodeType absent or "STEP") or a CONDITION — one flat record, like {@code GeneratedRecipeStepDTO}. */
    public record Node(
            String nodeType,
            String stepId,
            String action,
            String customActionName,
            List<Ingredient> ingredients,
            List<String> processes,
            List<String> fromSteps,
            String actionDescription,
            String expectedOutput,
            Double temperatureValue,
            String temperatureUnit,
            String flameLevel,
            String duration,
            String repeatInterval,
            String title,
            String expectedResult
    ) {}

    public record Ingredient(
            String ingredientId,
            Double quantity,
            String unit,
            String preparationStyle,
            String customIngredientName
    ) {}
}
