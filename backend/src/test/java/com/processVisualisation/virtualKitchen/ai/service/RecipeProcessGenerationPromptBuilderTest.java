package com.processVisualisation.virtualKitchen.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generation prompt's VOCABULARY section is rendered from the shared catalog — these checks make sure
 * per-action rules and the expanded ids actually reach the model, rather than a hardcoded list.
 */
class RecipeProcessGenerationPromptBuilderTest {

    private final RecipeProcessGenerationPromptBuilder builder =
            new RecipeProcessGenerationPromptBuilder(new RecipeStepVocabularyProvider());

    private final String prompt = builder.buildSystemPrompt();

    @Test
    void keepsAllStaticContentInTheSystemPromptSoItCanBeCached() {
        assertEquals(prompt, builder.buildSystemPrompt(), "the system prompt must be identical on every call");
        assertTrue(prompt.contains("VOCABULARY"));

        String userPrompt = builder.buildInitialPrompt("Boil pasta.");
        assertTrue(userPrompt.contains("Boil pasta."));
        assertFalse(userPrompt.contains("VOCABULARY"), "the per-request prompt carries only the recipe");
    }

    @Test
    void retryPromptIncludesThePreviousOutputAndItsErrors() {
        String retry = builder.buildRetryPrompt("Boil pasta.", "{\"mainProcess\":{}}", List.of("mainProcess.name is empty"));
        assertTrue(retry.contains("{\"mainProcess\":{}}"));
        assertTrue(retry.contains("mainProcess.name is empty"));
    }

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
