package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builders for recipe process graphs and catalog ingredients used by the recipe-order tests. */
final class RecipeOrderFixtures {

    static final long FLOUR = 101;
    static final long MILK = 102;
    static final long SALT = 103;
    static final long GARLIC = 104;

    private RecipeOrderFixtures() {
    }

    static Ingredient ingredient(long id, String name, UnitType shopUnit) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(id);
        ingredient.setName(name);
        ingredient.setDefaultUnit(shopUnit);
        return ingredient;
    }

    static Map<Long, Ingredient> catalog() {
        Map<Long, Ingredient> catalog = new LinkedHashMap<>();
        catalog.put(FLOUR, ingredient(FLOUR, "Flour", UnitType.KG));
        catalog.put(MILK, ingredient(MILK, "Milk", UnitType.LITER));
        catalog.put(SALT, ingredient(SALT, "Salt", UnitType.GRAM));
        catalog.put(GARLIC, ingredient(GARLIC, "Garlic", UnitType.GRAM));
        return catalog;
    }

    /** One raw ingredient line of a step: {ingredientId, quantity, unit}. */
    static Map<String, Object> line(Object ingredientId, Number quantity, String unit) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ingredientId", String.valueOf(ingredientId));
        line.put("quantity", quantity);
        line.put("unit", unit);
        return line;
    }

    /** A STEP node adding {@code lines}, optionally using subprocess results and earlier step outputs. */
    static Process.ProcessNode step(String id, String description, List<Map<String, Object>> lines,
                                    List<Long> subprocesses, List<String> fromSteps) {
        Map<String, Object> actionOn = new LinkedHashMap<>();
        actionOn.put("ingredients", new ArrayList<>(lines));
        List<Map<String, Object>> processRefs = new ArrayList<>();
        for (Long processId : subprocesses) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("processId", processId);
            processRefs.add(ref);
        }
        actionOn.put("processes", processRefs);
        List<Map<String, Object>> stepRefs = new ArrayList<>();
        for (String stepId : fromSteps) {
            Map<String, Object> ref = new LinkedHashMap<>();
            ref.put("stepId", stepId);
            stepRefs.add(ref);
        }
        actionOn.put("steps", stepRefs);
        Map<String, Object> stepData = new LinkedHashMap<>();
        stepData.put("action", "mix");
        stepData.put("actionDescription", description);
        stepData.put("actionOn", actionOn);
        Process.ProcessNode node = new Process.ProcessNode();
        node.setId(id);
        node.setKind(ProcessNodeKind.STEP);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", stepData);
        node.setData(data);
        return node;
    }

    static Process.ProcessNode step(String id, List<Map<String, Object>> lines) {
        return step(id, "Step " + id, lines, List.of(), List.of());
    }

    static Process.ProcessNode condition(String id) {
        Process.ProcessNode node = new Process.ProcessNode();
        node.setId(id);
        node.setKind(ProcessNodeKind.CONDITION);
        node.setData(new LinkedHashMap<>(Map.of("title", "Is it done?")));
        return node;
    }

    static Process process(long id, long recipeId, ProcessType type, String name, Process.ProcessNode... nodes) {
        Process process = new Process();
        process.setId(id);
        process.setRecipeId(recipeId);
        process.setType(type);
        process.setName(name);
        process.setNodes(new ArrayList<>(List.of(nodes)));
        process.setEdges(new ArrayList<>());
        return process;
    }

    /**
     * A pancake recipe: the "Batter" subprocess mixes 200 g flour + 250 ml milk; MAIN cooks the
     * batter (referencing the subprocess and a step output — no re-listed ingredients), seasons
     * with a pinch-free 2 g salt, and has a check. Requirements per batch: flour 0.2 kg, milk 0.25 l, salt 2 g.
     */
    static List<Process> pancakeProcesses(long recipeId) {
        Process batter = process(11, recipeId, ProcessType.SUBPROCESS, "Batter",
                step("b1", List.of(line(FLOUR, 200, "g"), line(MILK, 250, "ml"))),
                step("b2", "Whisk until smooth", List.of(), List.of(), List.of("b1")));
        Process main = process(10, recipeId, ProcessType.MAIN, "Main",
                step("m1", "Cook the batter", List.of(), List.of(11L), List.of()),
                condition("c1"),
                step("m2", "Season", List.of(line(SALT, 2, "g")), List.of(), List.of("m1")),
                step("m3", "Serve", List.of(), List.of(11L), List.of("m2")));
        return new ArrayList<>(List.of(main, batter));
    }
}
