package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedActionOnDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedActionOnIngredientDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedRecipeProcessDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedRecipeStepDTO;
import com.processVisualisation.virtualKitchen.recipe.validation.ProcessValidationResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RecipeProcessGenerationValidator} — the structural checks an
 * AI-generated semantic Process (MAIN + subprocesses) must pass before it is
 * returned to the frontend. Uses the real {@link RecipeStepVocabularyProvider}
 * (backed by the checked-in stepCatalogs.data.json) rather than mocking it,
 * so the vocabulary checks are exercised against real catalog ids.
 */
class RecipeProcessGenerationValidatorTest {

    private final RecipeProcessGenerationValidator validator = new RecipeProcessGenerationValidator(new RecipeStepVocabularyProvider());

    @Test
    void validate_simpleMainProcessOnly_isValid() {
        GeneratedRecipeProcessDTO main = process(null, "Chicken Curry", List.of(
                step("cut", actionOn(List.of(ingredient("onion", 2.0, "COUNT", "medium")), List.of()), "Cut the onions.", "Onions are diced.")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_multipleSubprocesses_isValid() {
        GeneratedRecipeProcessDTO main = process(null, "Chicken Curry", List.of(
                step("fry", actionOn(List.of(), List.of("marinate_chicken", "prepare_sauce")), "Cook everything together.", "Curry is ready.")
        ));
        GeneratedRecipeProcessDTO marinate = process("marinate_chicken", "Marinate Chicken", List.of(
                step("season", actionOn(List.of(ingredient("chicken", 500.0, "GRAM", null)), List.of()), "Season the chicken.", "Chicken is marinated.")
        ));
        GeneratedRecipeProcessDTO sauce = process("prepare_sauce", "Prepare Sauce", List.of(
                step("fry", actionOn(List.of(ingredient("tomato", 3.0, "COUNT", null)), List.of()), "Cook the tomatoes down.", "Sauce is thickened.")
        ));

        ProcessValidationResult result = validator.validate(main, List.of(marinate, sauce));

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_mainStepReferencingSubprocess_isValid() {
        GeneratedRecipeProcessDTO main = process(null, "Chicken Curry", List.of(
                step("fry", actionOn(List.of(), List.of("marinate_chicken")), "Add the marinated chicken and cook.", "Chicken is cooked through.")
        ));
        GeneratedRecipeProcessDTO marinate = process("marinate_chicken", "Marinate Chicken", List.of(
                step("season", actionOn(List.of(ingredient("chicken", 500.0, "GRAM", null)), List.of()), "Season the chicken.", "Chicken is marinated.")
        ));

        ProcessValidationResult result = validator.validate(main, List.of(marinate));

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_stepWithMultipleActionOnIngredients_isValid() {
        GeneratedRecipeProcessDTO main = process(null, "Veg Prep", List.of(
                step("cut", actionOn(List.of(
                        ingredient("onion", 2.0, "COUNT", "medium"),
                        ingredient("tomato", 3.0, "COUNT", "large"),
                        ingredient("chili", 5.0, "COUNT", "fine")
                ), List.of()), "Cut the vegetables.", "Vegetables are chopped.")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_differentPreparationStylesPerIngredient_areIndependentlyValid() {
        GeneratedActionOnIngredientDTO onion = ingredient("onion", 2.0, "COUNT", "medium");
        GeneratedActionOnIngredientDTO tomato = ingredient("tomato", 3.0, "COUNT", "large");
        GeneratedActionOnIngredientDTO chili = ingredient("chili", 5.0, "COUNT", "fine");

        GeneratedRecipeProcessDTO main = process(null, "Veg Prep", List.of(
                step("cut", actionOn(List.of(onion, tomato, chili), List.of()), "Cut everything.", "Cut into pieces.")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid());
        assertTrue("medium".equals(onion.getPreparationStyle()) && "large".equals(tomato.getPreparationStyle()) && "fine".equals(chili.getPreparationStyle()),
                "each ingredient must keep its own independent preparation style");
    }

    @Test
    void validate_missingActionDescription_isRejected() {
        GeneratedRecipeStepDTO badStep = step("cut", actionOn(List.of(ingredient("onion", 1.0, "COUNT", null)), List.of()), null, "Diced.");
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(badStep));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("actionDescription")));
    }

    @Test
    void validate_missingExpectedOutput_isRejected() {
        GeneratedRecipeStepDTO badStep = step("cut", actionOn(List.of(ingredient("onion", 1.0, "COUNT", null)), List.of()), "Cut the onion.", null);
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(badStep));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("expectedOutput")));
    }

    @Test
    void validate_blankExpectedOutput_isValid() {
        // expectedOutput may legitimately be an empty string when the recipe gives no basis for one.
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("wait", actionOn(List.of(), List.of()), "Let it rest.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_missingOptionalAdvancedProperties_isValid() {
        GeneratedRecipeStepDTO stepDto = step("cut", actionOn(List.of(ingredient("onion", 1.0, "COUNT", null)), List.of()), "Cut the onion.", "Diced.");
        // temperature/flameLevel/duration deliberately left null — never set below.
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(stepDto));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_unknownAction_isRejected() {
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("teleport", actionOn(List.of(), List.of()), "Do something unknown.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("action")));
    }

    @Test
    void validate_unknownIngredient_isRejected() {
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("cut", actionOn(List.of(ingredient("dragonfruit", 1.0, "COUNT", null)), List.of()), "Cut it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("ingredientId")));
    }

    @Test
    void validate_customIngredientWithoutName_isRejected() {
        GeneratedActionOnIngredientDTO custom = ingredient("custom", 1.0, "COUNT", null);
        custom.setCustomIngredientName(null);
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("add", actionOn(List.of(custom), List.of()), "Add it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("customIngredientName")));
    }

    @Test
    void validate_customIngredientWithName_isValid() {
        GeneratedActionOnIngredientDTO custom = ingredient("custom", 1.0, "COUNT", null);
        custom.setCustomIngredientName("Dragonfruit");
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("add", actionOn(List.of(custom), List.of()), "Add it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_invalidUnit_isRejected() {
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("cut", actionOn(List.of(ingredient("onion", 1.0, "cups", null)), List.of()), "Cut it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("unit")));
    }

    @Test
    void validate_negativeQuantity_isRejected() {
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("cut", actionOn(List.of(ingredient("onion", -1.0, "COUNT", null)), List.of()), "Cut it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("quantity")));
    }

    @Test
    void validate_unknownSubprocessReference_isRejected() {
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("cook", actionOn(List.of(), List.of("does_not_exist")), "Cook it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("unknown subprocess ref")));
    }

    @Test
    void validate_selfSubprocessReference_isRejected() {
        GeneratedRecipeProcessDTO subprocess = process("loopy", "Loopy", List.of(
                step("cook", actionOn(List.of(), List.of("loopy")), "Cook it.", "")
        ));
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("cook", actionOn(List.of(), List.of("loopy")), "Cook it.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of(subprocess));

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("self-reference")));
    }

    @Test
    void validate_circularSubprocessReferences_isRejected() {
        GeneratedRecipeProcessDTO a = process("a", "A", List.of(
                step("cook", actionOn(List.of(), List.of("b")), "Use B.", "")
        ));
        GeneratedRecipeProcessDTO b = process("b", "B", List.of(
                step("cook", actionOn(List.of(), List.of("a")), "Use A.", "")
        ));
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(
                step("cook", actionOn(List.of(), List.of("a")), "Use A.", "")
        ));

        ProcessValidationResult result = validator.validate(main, List.of(a, b));

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("circular")));
    }

    @Test
    void validate_conditionStep_requiresTitleAndValidExpectedResult() {
        GeneratedRecipeStepDTO condition = new GeneratedRecipeStepDTO();
        condition.setNodeType("CONDITION");
        condition.setActionDescription("Check if the water is boiling.");
        condition.setExpectedOutput("Water is at a rolling boil.");
        // title intentionally left null, expectedResult intentionally invalid
        condition.setExpectedResult("maybe");

        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(condition));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("title")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("expectedResult")));
    }

    @Test
    void validate_validConditionStep_isValid() {
        GeneratedRecipeStepDTO condition = new GeneratedRecipeStepDTO();
        condition.setNodeType("CONDITION");
        condition.setTitle("Is the water boiling?");
        condition.setExpectedResult("success");
        condition.setActionDescription("Check if the water is boiling.");
        condition.setExpectedOutput("Water is at a rolling boil.");

        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of(condition));

        ProcessValidationResult result = validator.validate(main, List.of());

        assertTrue(result.isValid(), () -> String.join("; ", result.getErrors()));
    }

    @Test
    void validate_missingMainProcess_isRejected() {
        ProcessValidationResult result = validator.validate(null, List.of());

        assertFalse(result.isValid());
    }

    @Test
    void validate_mainProcessWithNoSteps_isRejected() {
        GeneratedRecipeProcessDTO main = process(null, "Recipe", List.of());

        ProcessValidationResult result = validator.validate(main, List.of());

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("no steps")));
    }

    // --- helpers -------------------------------------------------------

    private GeneratedRecipeProcessDTO process(String ref, String name, List<GeneratedRecipeStepDTO> steps) {
        GeneratedRecipeProcessDTO dto = new GeneratedRecipeProcessDTO();
        dto.setRef(ref);
        dto.setName(name);
        dto.setSteps(new ArrayList<>(steps));
        return dto;
    }

    private GeneratedRecipeStepDTO step(String action, GeneratedActionOnDTO actionOn, String actionDescription, String expectedOutput) {
        GeneratedRecipeStepDTO dto = new GeneratedRecipeStepDTO();
        dto.setNodeType("STEP");
        dto.setAction(action);
        dto.setActionOn(actionOn);
        dto.setActionDescription(actionDescription);
        dto.setExpectedOutput(expectedOutput);
        return dto;
    }

    private GeneratedActionOnDTO actionOn(List<GeneratedActionOnIngredientDTO> ingredients, List<String> processes) {
        return new GeneratedActionOnDTO(new ArrayList<>(ingredients), new ArrayList<>(processes));
    }

    private GeneratedActionOnIngredientDTO ingredient(String ingredientId, Double quantity, String unit, String preparationStyle) {
        return new GeneratedActionOnIngredientDTO(ingredientId, quantity, unit, preparationStyle, null);
    }
}
