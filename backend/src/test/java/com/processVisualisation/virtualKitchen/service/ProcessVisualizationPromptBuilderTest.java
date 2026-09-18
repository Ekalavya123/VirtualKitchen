package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationInput;
import com.processVisualisation.virtualKitchen.ai.service.ProcessVisualizationPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the two prompt-shaping rules the brief calls out explicitly: a step with more than one
 * Action On ingredient must ask for a single composite image (never multiple images), and a
 * previous step's expected output must be surfaced as the continuity signal when present.
 */
class ProcessVisualizationPromptBuilderTest {

    private final ProcessVisualizationPromptBuilder builder = new ProcessVisualizationPromptBuilder();

    @Test
    void buildUserPrompt_singleIngredient_doesNotAskForACompositeImage() {
        ProcessVisualizationInput input = new ProcessVisualizationInput(
                "Cut", List.of(new ProcessVisualizationInput.IngredientTarget("Onion", "2", "", "Medium")),
                List.of(), "Cut the onion", "Chopped onion", "", "", "", null);

        String prompt = builder.buildUserPrompt(input);

        assertFalse(prompt.contains("multiple simultaneous"), "a single-ingredient step should not trigger composite-image instructions");
    }

    @Test
    void buildUserPrompt_multipleIngredients_asksForOneCompositeImageNotMultiple() {
        ProcessVisualizationInput input = new ProcessVisualizationInput(
                "Cut",
                List.of(
                        new ProcessVisualizationInput.IngredientTarget("Onion", "2", "", "Medium"),
                        new ProcessVisualizationInput.IngredientTarget("Tomato", "3", "", "Large"),
                        new ProcessVisualizationInput.IngredientTarget("Chili", "1", "", "Fine")),
                List.of(), "Cut the vegetables", "Prepped vegetables", "", "", "", null);

        String prompt = builder.buildUserPrompt(input);

        assertTrue(prompt.contains("multiple simultaneous"));
        assertTrue(prompt.contains("single") && prompt.toLowerCase().contains("composition"));
        assertTrue(prompt.contains("Do not describe separate images"));
    }

    @Test
    void buildUserPrompt_withPreviousExpectedOutput_includesItForContinuity() {
        ProcessVisualizationInput input = new ProcessVisualizationInput(
                "Fry", List.of(), List.of(), "Fry the onions until golden brown", "Golden-brown fried onions",
                "", "", "", "Chopped onions");

        String prompt = builder.buildUserPrompt(input);

        assertTrue(prompt.contains("Chopped onions"));
        assertTrue(prompt.contains("visual continuity"));
    }

    @Test
    void buildUserPrompt_firstStep_hasNoPreviousStepText() {
        ProcessVisualizationInput input = new ProcessVisualizationInput(
                "Cut", List.of(), List.of(), "Cut the onion", "Chopped onion", "", "", "", null);

        String prompt = builder.buildUserPrompt(input);

        assertTrue(prompt.contains("first step"));
    }
}
