package com.processVisualisation.virtualKitchen.recipeorder.service;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.IngredientRequirement;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.RequirementIssue;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder.RequirementSource;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionException;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService.Basis;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService.ConversionResult;
import com.processVisualisation.virtualKitchen.store.units.UnitType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Works out which ingredients — and how much of each — a recipe order needs, from the recipe's
 * process graphs.
 * <p>
 * Semantics (the same ones the recipe's Ingredients view uses): every STEP of every process of the
 * recipe (the main process and each subprocess, each process counted once however many steps use
 * its result) lists the raw ingredients it adds in its "Action On" ingredients. A step that works on
 * an earlier step's or subprocess's result references that output instead of re-listing the
 * ingredient, so summing the raw ingredient lines counts each ingredient exactly as often as the
 * recipe adds it — referencing a node or a subprocess never counts its ingredients again.
 * <p>
 * Each line is scaled by the order's serving factor and converted into the ingredient's
 * shop/inventory unit. A line that can't be resolved safely (a custom ingredient that isn't in the
 * catalog, an unknown id, a unit without a conversion) becomes a {@link RequirementIssue}, which
 * blocks the order — it is never counted as zero.
 */
public final class RecipeRequirementCalculator {

    public record Result(List<IngredientRequirement> requirements, List<RequirementIssue> issues, int stepCount) {}

    private final UnitConversionService conversions;

    public RecipeRequirementCalculator(UnitConversionService conversions) {
        this.conversions = conversions;
    }

    /**
     * @param ingredientsById the catalog ingredients the processes reference (missing ids become issues)
     */
    public Result calculate(List<Process> processes, double scale, Map<Long, Ingredient> ingredientsById) {
        Map<Long, IngredientRequirement> byIngredient = new LinkedHashMap<>();
        List<RequirementIssue> issues = new ArrayList<>();
        // One issue per uncatalogued ingredient however many steps use it: the list of things to resolve.
        Map<String, CustomUse> customUses = new LinkedHashMap<>();
        Set<Long> seenProcesses = new HashSet<>();
        int stepCount = 0;

        for (Process process : processes) {
            if (process == null || (process.getId() != null && !seenProcesses.add(process.getId()))) continue;
            for (Process.ProcessNode node : process.getNodes() == null ? List.<Process.ProcessNode>of() : process.getNodes()) {
                if (node == null || node.getKind() != ProcessNodeKind.STEP) continue;
                stepCount++;
                for (Map<?, ?> line : ingredientLines(node)) {
                    addLine(process, node, line, scale, ingredientsById, byIngredient, issues, customUses);
                }
            }
        }

        customUses.values().forEach(use -> use.issue().setMessage("\"" + use.issue().getName() + "\" isn't in the ingredient catalog"
                + (use.steps() > 1 ? " (used in " + use.steps() + " steps)" : "")
                + ", so it can't be checked against your kitchen inventory. Match it to a catalog ingredient in the recipe editor."));

        List<IngredientRequirement> requirements = new ArrayList<>(byIngredient.values());
        requirements.forEach(requirement -> requirement.setRequiredQuantity(round(requirement.getRequiredQuantity())));
        return new Result(requirements, issues, stepCount);
    }

    /** An uncatalogued ingredient's single issue and how many steps use it. */
    private record CustomUse(RequirementIssue issue, int steps) {}

    private void addLine(Process process, Process.ProcessNode node, Map<?, ?> line, double scale,
                         Map<Long, Ingredient> ingredientsById, Map<Long, IngredientRequirement> byIngredient,
                         List<RequirementIssue> issues, Map<String, CustomUse> customUses) {
        String ref = asString(line.get("ingredientId"));
        String customName = asString(line.get("customIngredientName"));
        if (ref == null || ref.isBlank()) return; // an empty picker row adds nothing

        Long ingredientId = parseId(ref);
        if (ingredientId == null) {
            String name = customName != null && !customName.isBlank() ? customName.trim() : ref;
            CustomUse use = customUses.get(name.toLowerCase(Locale.ROOT));
            if (use == null) {
                RequirementIssue issue = issue(ref, name, process, node, "");
                issues.add(issue);
                customUses.put(name.toLowerCase(Locale.ROOT), new CustomUse(issue, 1));
            } else {
                customUses.put(name.toLowerCase(Locale.ROOT), new CustomUse(use.issue(), use.steps() + 1));
            }
            return;
        }
        Ingredient ingredient = ingredientsById.get(ingredientId);
        if (ingredient == null) {
            issues.add(issue(ref, "Ingredient #" + ingredientId, process, node,
                    "Ingredient #" + ingredientId + " is no longer in the ingredient catalog."));
            return;
        }

        Double quantity = asDouble(line.get("quantity"));
        String unit = asString(line.get("unit"));
        UnitType target = ingredient.getDefaultUnit() != null ? ingredient.getDefaultUnit() : UnitType.GRAM;
        ConversionResult converted;
        try {
            converted = conversions.convert(quantity, unit, target, ingredient);
        } catch (UnitConversionException e) {
            String customUnit = asString(line.get("customUnit"));
            String message = "custom".equals(unit)
                    ? ingredient.getName() + ": the unit \"" + (customUnit == null || customUnit.isBlank() ? "custom" : customUnit.trim())
                        + "\" can't be converted. Use a standard unit for it in the recipe editor."
                    : ingredient.getName() + ": " + e.getMessage() + ".";
            issues.add(issue(ref, ingredient.getName(), process, node, message));
            return;
        }
        double amount = converted.value() * scale;

        IngredientRequirement requirement = byIngredient.computeIfAbsent(ingredientId, id -> {
            IngredientRequirement created = new IngredientRequirement();
            created.setIngredientId(id);
            created.setName(ingredient.getName());
            created.setIcon(ingredient.getIcon());
            created.setImageUrl(ingredient.getImageUrl());
            created.setUnit(target);
            created.setBasis(Basis.EXACT.name());
            return created;
        });
        requirement.setRequiredQuantity(requirement.getRequiredQuantity() + amount);
        requirement.setBasis(lessCertain(requirement.getBasis(), converted.basis()).name());
        if (converted.note() != null && !requirement.getConversionNotes().contains(converted.note())) {
            requirement.getConversionNotes().add(converted.note());
        }

        RequirementSource source = new RequirementSource();
        source.setProcessId(process.getId());
        source.setProcessName(process.getName());
        source.setNodeId(node.getId());
        source.setQuantity(quantity);
        source.setUnit(unit);
        source.setConvertedQuantity(round(amount));
        requirement.getSources().add(source);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> ingredientLines(Process.ProcessNode node) {
        if (node.getData() == null || !(node.getData().get("step") instanceof Map<?, ?> step)) return List.of();
        if (!(step.get("actionOn") instanceof Map<?, ?> actionOn)) return List.of();
        if (!(actionOn.get("ingredients") instanceof List<?> lines)) return List.of();
        List<Map<?, ?>> result = new ArrayList<>();
        for (Object line : lines) {
            if (line instanceof Map<?, ?> map) result.add(map);
        }
        return result;
    }

    private static RequirementIssue issue(String ref, String name, Process process, Process.ProcessNode node, String message) {
        RequirementIssue issue = new RequirementIssue();
        issue.setIngredientRef(ref);
        issue.setName(name);
        issue.setProcessName(process.getName());
        issue.setNodeId(node.getId());
        issue.setMessage(message);
        return issue;
    }

    private static Basis lessCertain(String current, Basis next) {
        Basis existing = current == null ? Basis.EXACT : Basis.valueOf(current);
        return existing.ordinal() >= next.ordinal() ? existing : next;
    }

    static Long parseId(String ref) {
        if (ref == null || ref.isBlank() || !ref.chars().allMatch(Character::isDigit)) return null;
        try {
            return Long.parseLong(ref);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double asDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static double round(double value) {
        return Math.round(value * 1_000_000d) / 1_000_000d;
    }

    /** Ingredient ids the processes reference — what to load before {@link #calculate}. */
    public static Set<Long> referencedIngredientIds(List<Process> processes) {
        Set<Long> ids = new HashSet<>();
        for (Process process : processes) {
            if (process == null || process.getNodes() == null) continue;
            for (Process.ProcessNode node : process.getNodes()) {
                if (node == null || node.getKind() != ProcessNodeKind.STEP) continue;
                for (Map<?, ?> line : ingredientLines(node)) {
                    Long id = parseId(asString(line.get("ingredientId")));
                    if (id != null) ids.add(id);
                }
            }
        }
        return ids;
    }
}
