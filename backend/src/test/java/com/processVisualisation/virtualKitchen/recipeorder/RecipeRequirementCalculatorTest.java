package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.IngredientRequirement;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.processVisualisation.virtualKitchen.recipeorder.RecipeOrderFixtures.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeRequirementCalculatorTest {

    private final RecipeRequirementCalculator calculator = new RecipeRequirementCalculator(UnitConversionService.withDefaults());

    @Test
    void includesSubprocessIngredientsWithoutCountingReferencesAgain() {
        // The Batter subprocess is referenced by two MAIN steps and step outputs are reused —
        // neither re-counts flour or milk.
        RecipeRequirementCalculator.Result result = calculator.calculate(pancakeProcesses(1), 1, catalog());

        assertTrue(result.issues().isEmpty(), () -> result.issues().toString());
        assertEquals(3, result.requirements().size());
        assertRequirement(result, FLOUR, 0.2, UnitType.KG);
        assertRequirement(result, MILK, 0.25, UnitType.LITER);
        assertRequirement(result, SALT, 2, UnitType.GRAM);
        assertEquals(5, result.stepCount(), "STEP nodes only, conditions excluded");
    }

    @Test
    void sumsTheSameIngredientAddedInSeveralStepsAndUnits() {
        Process main = process(20, 2, ProcessType.MAIN, "Main",
                step("s1", List.of(line(FLOUR, 500, "g"))),
                step("s2", List.of(line(FLOUR, 0.25, "kg"))));
        RecipeRequirementCalculator.Result result = calculator.calculate(List.of(main), 1, catalog());
        assertRequirement(result, FLOUR, 0.75, UnitType.KG);
        assertEquals(2, result.requirements().get(0).getSources().size());
    }

    @Test
    void countsAProcessDocumentOnlyOnce() {
        List<Process> processes = new ArrayList<>(pancakeProcesses(1));
        processes.add(processes.get(1)); // the Batter subprocess listed twice
        assertRequirement(calculator.calculate(processes, 1, catalog()), FLOUR, 0.2, UnitType.KG);
    }

    @Test
    void scalesEveryQuantityByTheServingFactor() {
        RecipeRequirementCalculator.Result result = calculator.calculate(pancakeProcesses(1), 2.5, catalog());
        assertRequirement(result, FLOUR, 0.5, UnitType.KG);
        assertRequirement(result, MILK, 0.625, UnitType.LITER);
        assertRequirement(result, SALT, 5, UnitType.GRAM);
    }

    @Test
    void reportsUnresolvableLinesAsIssuesInsteadOfZero() {
        Map<String, Object> custom = line("custom", 1, "piece");
        custom.put("customIngredientName", "Dragon fruit");
        Process main = process(30, 3, ProcessType.MAIN, "Main",
                step("s1", List.of(custom)),
                step("s2", List.of(line(999, 1, "g"))),          // not in the catalog
                step("s3", List.of(line(SALT, 1, "furlong"))),    // no conversion for the unit
                step("s4", List.of(line(SALT, null, "g"))),       // quantity missing
                step("s5", List.of(line(GARLIC, 2, "clove"))));   // converts with a default factor

        RecipeRequirementCalculator.Result result = calculator.calculate(List.of(main), 1, catalog());

        assertEquals(4, result.issues().size(), () -> result.issues().toString());
        assertTrue(result.issues().get(0).getMessage().contains("Dragon fruit"));
        assertEquals(1, result.requirements().size());
        IngredientRequirement garlic = result.requirements().get(0);
        assertEquals(10, garlic.getRequiredQuantity(), 1e-9);
        assertEquals("DEFAULT_FACTOR", garlic.getBasis());
    }

    private static void assertRequirement(RecipeRequirementCalculator.Result result, long ingredientId, double quantity, UnitType unit) {
        IngredientRequirement requirement = result.requirements().stream()
                .filter(candidate -> candidate.getIngredientId() == ingredientId)
                .findFirst().orElseThrow(() -> new AssertionError("no requirement for " + ingredientId));
        assertEquals(quantity, requirement.getRequiredQuantity(), 1e-9);
        assertEquals(unit, requirement.getUnit());
    }
}
