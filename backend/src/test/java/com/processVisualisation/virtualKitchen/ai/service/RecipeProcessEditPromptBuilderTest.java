package com.processVisualisation.virtualKitchen.ai.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.processVisualisation.virtualKitchen.ai.service.EditTestFixtures.BOIL_ID;
import static com.processVisualisation.virtualKitchen.ai.service.EditTestFixtures.COOK_ID;
import static com.processVisualisation.virtualKitchen.ai.service.EditTestFixtures.pastaProcess;
import static org.assertj.core.api.Assertions.assertThat;

class RecipeProcessEditPromptBuilderTest {

    private static final RecipeStepVocabularyProvider VOCABULARY = new RecipeStepVocabularyProvider();
    private static final RecipeProcessOutputSchema GENERATION_SCHEMA = new RecipeProcessOutputSchema(VOCABULARY);
    private final RecipeProcessEditPromptBuilder builder = new RecipeProcessEditPromptBuilder(
            new RecipeProcessGenerationPromptBuilder(VOCABULARY, GENERATION_SCHEMA),
            new RecipeProcessEditOutputSchema(VOCABULARY, GENERATION_SCHEMA));

    @Test
    void userPrompt_showsAliasesConnectionsAndSubprocesses_neverNodeIds() {
        String prompt = builder.buildInitialPrompt(RecipeProcessEditContext.of(pastaProcess(), COOK_ID), "Cook it for 8 minutes");

        assertThat(prompt)
                .contains("CURRENT PROCESS \"Main Process\"")
                .contains("{\"id\":\"s1\",\"step\":1,\"action\":\"boil\"")
                .contains("\"fromSteps\":[\"s2\"]")
                .contains("\"next\":[\"s4\"]")
                .contains("- p1: Prepare Sauce")
                .contains("SELECTED STEP: s3")
                .contains("INSTRUCTION:\nCook it for 8 minutes")
                .doesNotContain(BOIL_ID)
                .doesNotContain(COOK_ID);
    }

    @Test
    void userPrompt_omitsEmptyFields() {
        String prompt = builder.buildInitialPrompt(RecipeProcessEditContext.of(pastaProcess(), null), "x");

        // s4 (drain) has no ingredients, flame or duration, and leads nowhere.
        assertThat(prompt).contains("{\"id\":\"s4\",\"step\":4,\"action\":\"drain\",\"fromSteps\":[\"s3\"],"
                + "\"actionDescription\":\"Drain the pasta\",\"expectedOutput\":\"drained pasta\"}");
        assertThat(prompt).contains("SELECTED STEP: none").doesNotContain("null");
    }

    @Test
    void retryPrompt_carriesPreviousOutputAndErrors() {
        String prompt = builder.buildRetryPrompt(RecipeProcessEditContext.of(pastaProcess(), null), "Remove the onion step",
                "{\"summary\":\"x\"}", List.of("operations[0].target references an unknown node: s9"));

        assertThat(prompt).contains("YOUR PREVIOUS OUTPUT:\n{\"summary\":\"x\"}").contains("unknown node: s9");
    }

    @Test
    void systemPrompt_reusesGenerationStepRulesAndListsEveryOperation() {
        String system = builder.buildSystemPrompt();

        assertThat(system)
                .contains("return the SMALLEST set of edit")
                .contains("CHOOSING THE ACTION")
                .contains("VOCABULARY")
                .contains("- ADD_STEP {op!, after!, step!}")
                .contains("- REPLACE_INGREDIENT {op!, target, from!, ingredient!}")
                .contains("- MOVE_NODE {op!, target!, after!}");
    }
}
