package com.processVisualisation.virtualKitchen.ai.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generation prompt's VOCABULARY section is rendered from the shared catalog — these checks make sure
 * per-action rules and the expanded ids actually reach the model, rather than a hardcoded list.
 */
class RecipeProcessGenerationPromptBuilderTest {

    private final String prompt = new RecipeProcessGenerationPromptBuilder(new RecipeStepVocabularyProvider())
            .buildInitialPrompt("Boil pasta.");

    @Test
    void rendersActionsWithTargetsAndFields() {
        assertTrue(prompt.contains("- bake | I+S! | quantity, preparationStyle{"));
        assertTrue(prompt.contains("temperature(oven)*, duration*"), "recommended fields are marked with '*'");
        assertTrue(prompt.contains("- preheat | - |"), "a targetless action is rendered with '-'");
        assertTrue(prompt.contains("- chop | I! |"), "an ingredient-only action is rendered as I");
    }

    @Test
    void rendersIngredientsWithDefaultUnitAndAliases() {
        assertTrue(prompt.contains("garlic (clove) [lahsun"));
        assertTrue(prompt.contains("chickpea-flour (cup) [besan"));
    }

    @Test
    void rendersCatalogUnitsInsteadOfTheLegacyEnum() {
        assertTrue(prompt.contains("tbsp"));
        assertTrue(prompt.contains("(quantity must be null): to-taste, as-needed"));
        assertFalse(prompt.contains("COUNT|GRAM|KG|ML|LITER"));
    }

    @Test
    void asksForStructuredTemperatureAndCustomActionName() {
        assertTrue(prompt.contains("\"temperatureValue\""));
        assertTrue(prompt.contains("\"customActionName\""));
        assertTrue(prompt.contains("\"repeatInterval\""));
    }

    @Test
    void describesStepIdsAndStepOutputReferences() {
        assertTrue(prompt.contains("\"stepId\": \"s1\""));
        assertTrue(prompt.contains("\"steps\": []"));
        assertTrue(prompt.contains("STEP OUTPUTS"));
    }
}
