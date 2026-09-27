package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.ActionDefinition;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FieldRequirement;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.IngredientDefinition;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.UnitDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FIELD_DURATION;
import static com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FIELD_FLAME_LEVEL;
import static com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FIELD_PREPARATION_STYLE;
import static com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FIELD_QUANTITY;
import static com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FIELD_REPEAT_INTERVAL;
import static com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FIELD_TEMPERATURE;

/**
 * Builds the system and user prompts used to convert free-form recipe text
 * into a semantic Process structure (MAIN process + subprocesses, each a
 * flat list of STEP/CONDITION nodes). No React Flow field (id, position,
 * dimensions, handles) is ever requested — the application generates those
 * when converting the result into the Process working snapshot.
 * <p>
 * The VOCABULARY section is rendered entirely from {@link RecipeStepVocabularyProvider}
 * (the shared stepCatalogs.data.json), including each action's allowed targets and fields.
 */
@Component
public class RecipeProcessGenerationPromptBuilder {

    /** Aliases shown per ingredient — enough to recognise regional names without bloating the prompt. */
    private static final int MAX_INGREDIENT_ALIASES = 3;

    /** Prompt-facing name of each catalog field key (the output JSON key the model writes). */
    private static final Map<String, String> FIELD_OUTPUT_NAMES = Map.of(
            FIELD_QUANTITY, "quantity",
            FIELD_PREPARATION_STYLE, "preparationStyle",
            FIELD_TEMPERATURE, "temperature",
            FIELD_FLAME_LEVEL, "flameLevel",
            FIELD_DURATION, "duration",
            FIELD_REPEAT_INTERVAL, "repeatInterval"
    );

    private final String vocabularyBlock;

    public RecipeProcessGenerationPromptBuilder(RecipeStepVocabularyProvider recipeStepVocabularyProvider) {
        this.vocabularyBlock = buildVocabularyBlock(recipeStepVocabularyProvider);
    }

    public String buildSystemPrompt() {
        return """
                Convert recipe text into a semantic recipe process structure.

                Return ONLY valid JSON.
                Do not return markdown, explanations, comments, or code fences.
                Do not include node ids, edge ids, positions, width, height, handles, or any other
                React Flow or UI presentation field. Only semantic recipe/process data.
                """;
    }

    public String buildInitialPrompt(String recipeText) {
        return buildSchemaAndRulesBlock()
                + "\nRECIPE:\n"
                + recipeText
                + "\n\nReturn the JSON process structure only.";
    }

    public String buildRetryPrompt(String recipeText, String previousOutput, List<String> validationErrors) {
        return buildSchemaAndRulesBlock()
                + "\nRECIPE:\n"
                + recipeText
                + "\n\nVALIDATION ERRORS:\n"
                + String.join("; ", validationErrors)
                + "\n\nReturn corrected JSON only.";
    }

    private String buildSchemaAndRulesBlock() {
        return """
                OUTPUT
                Return exactly this shape:
                {
                  "mainProcess": { "name": "", "steps": [] },
                  "subprocesses": [
                    { "ref": "short_unique_slug", "name": "", "steps": [] }
                  ]
                }

                PROCESS MODEL
                - A recipe has exactly one MAIN process and zero or more SUBPROCESSes.
                - Each process (MAIN or a subprocess) is a flat, ordered list of STEP/CONDITION nodes — never nested.
                - Do not create a subprocess inside another subprocess. Only MAIN and top-level subprocesses exist.
                - Create a subprocess only for a meaningful intermediate preparation that stands on its own
                  (e.g. "Marinate Chicken", "Prepare Sauce"). Do not decompose the recipe into many tiny subprocesses.
                - "subprocesses" may be an empty array when the recipe has no meaningful separate preparation stage.
                - A subprocess is referenced from a STEP's own actionOn.processes — NEVER as its own node. There is
                  no node type for "run this subprocess"; the step whose action uses the subprocess's result
                  references it there instead.
                - "ref" is a short, unique, lowercase snake_case slug (e.g. "marinate_chicken") used only to let a
                  STEP's actionOn.processes point at that subprocess within this same response. It is not a
                  database id and the MAIN process itself has no ref (nothing can reference MAIN).

                STEPS ARE ORDERED
                - Each process's "steps" array is already in execution order — the application connects
                  consecutive steps automatically. Do not include edges, connections, or ordering fields.
                - Create a separate step for each meaningful cooking action; split multiple actions in one
                  sentence into separate steps; do not split one atomic action into multiple steps.

                NODE TYPES

                1. STEP — a concrete cooking action.
                {
                  "nodeType": "STEP",
                  "stepId": "s1",
                  "action": "",
                  "customActionName": "",
                  "actionOn": {
                    "ingredients": [
                      { "ingredientId": "", "quantity": 1, "unit": "", "preparationStyle": "", "customIngredientName": "" }
                    ],
                    "processes": [],
                    "steps": []
                  },
                  "actionDescription": "",
                  "expectedOutput": "",
                  "temperatureValue": null,
                  "temperatureUnit": "",
                  "flameLevel": "",
                  "duration": "",
                  "repeatInterval": ""
                }

                2. CONDITION — a decision/check/repetition (if, otherwise, until, unless, check, verify, repeat until).
                {
                  "nodeType": "CONDITION",
                  "title": "",
                  "expectedResult": "success",
                  "actionDescription": "",
                  "expectedOutput": ""
                }
                Do not create a condition for an ordinary cooking instruction that isn't actually a check.

                CHOOSING THE ACTION
                - action must be an id from ACTIONS below. Use the most specific action that fits
                  (e.g. "saute", "simmer", "deep-fry" rather than "cook"; "mince" rather than "cut").
                - Use "cook" only when the recipe names no technique at all.
                - Use "custom" only when no listed action can represent the operation, and then always set
                  "customActionName" to a short verb phrase. Leave customActionName empty otherwise.
                - Respect each action's "on" targets: never put ingredients on an action that only acts on
                  subprocesses, never reference subprocesses from an ingredient-only action, and never give a
                  single-ingredient action ("1I") more than one ingredient.

                ACTION ON — INGREDIENT-SPECIFIC PROPERTIES
                - actionOn.ingredients is a list; a step's action can apply to multiple ingredients at once
                  (e.g. Chop applied to onion, tomato, and chilli, each with its own style).
                - EACH ingredient entry has its OWN quantity/unit/preparationStyle. Never put quantity, unit, or
                  preparation style at the step level — only inside that ingredient's own entry.
                - quantity: the number the recipe states for that ingredient in this step. Use null when the recipe
                  gives no amount (and for units that carry no number: to-taste, as-needed). When an action marks
                  quantity as required ("quantity!") and the recipe gives no amount, use 1 with the ingredient's
                  default unit.
                - unit: the recipe's unit mapped to a unit id below (e.g. "2 cloves garlic" -> quantity 2, unit
                  "clove"; "salt to taste" -> quantity null, unit "to-taste"). When no unit is stated, use the
                  ingredient's default unit shown in parentheses.
                - preparationStyle: only when the action lists "preparationStyle" and the recipe states or clearly
                  implies it (e.g. "finely chopped" -> "finely-chopped"). It must come from that action's
                  preparation style sets. Never invent one. An ingredient's state as it is added
                  (e.g. "500 g boneless chicken", "2 onions, sliced") belongs on that ingredient entry.
                - actionOn.processes lists the "ref" of any subprocess this step's action uses the result of
                  (e.g. a "saute" step referencing "prepare_masala"). Leave it empty when the step doesn't use a
                  subprocess's output.
                - A step can have ingredients, subprocess references, step-output references, any combination of
                  them, or — only for actions whose target is optional — none.

                STEP OUTPUTS (REUSING AN EARLIER STEP'S RESULT)
                - Give every STEP a "stepId": a short slug unique within its own process ("s1", "s2", ...).
                - actionOn.steps lists the stepIds of EARLIER STEPs in the SAME process whose expectedOutput this
                  step acts on. Example: s1 boils eggs (expectedOutput "boiled eggs"); s2 fries them with onions ->
                  s2.actionOn.steps = ["s1"], s2.actionOn.ingredients = [onion]. Do not list the eggs again as an
                  ingredient in s2 — the step output already stands for them.
                - Only reference a step that comes before this one in the "steps" array, is a STEP (never a
                  CONDITION), is not this step itself, and has a non-empty expectedOutput.
                - Step outputs count as prepared components, so only actions whose targets include S may use them.
                - Use actionOn.processes (not steps) for the result of a whole subprocess.

                CORE STEP FIELDS
                - actionDescription: a short natural-language description of what this step does. Always include it.
                - expectedOutput: the expected result/state after this step. Include it whenever it can be
                  reasonably inferred from the recipe (e.g. "onions turn golden brown"); leave it as an empty
                  string only when the recipe gives no basis for it. Do not invent specific, unstated outcomes.

                ADVANCED PROPERTIES (OPTIONAL, ACTION-SPECIFIC)
                - Only use temperatureValue/temperatureUnit, flameLevel, duration, repeatInterval when the chosen
                  action lists that field AND the recipe states it or it can be determined reliably. Otherwise use
                  null/empty strings. Never invent a temperature, heat level or duration.
                - temperatureValue is a number and temperatureUnit is "C" or "F" (both or neither). What the number
                  means is given by the action's temperature context (oven/oil/liquid/surface/ambient) — an oven
                  temperature is not a heat level.
                - flameLevel is the burner/appliance setting (e.g. "medium-high"), not a temperature.
                - duration and repeatInterval are written as "<number> <seconds|minutes|hours>" (e.g. "5 minutes").
                  For a range ("5-7 minutes") use the lower bound and keep the range in actionDescription. Convert
                  pressure-cooker whistles to an approximate duration and keep the whistle count in actionDescription.
                - repeatInterval is how often the action repeats during the step (e.g. "stir every 2 minutes" ->
                  action "stir", repeatInterval "2 minutes").

                """
                + vocabularyBlock
                + """

                GENERAL RULES
                - Output ids exactly as listed. Aliases in [brackets] exist only to help you read the recipe —
                  never output an alias, a label, or an id that is not listed.
                - Use "customIngredientName" with ingredientId "custom" only when no listed ingredient reasonably
                  matches — never silently map an unlisted ingredient onto the wrong catalog entry.
                - Keep quantities, units, durations, temperatures, and cooking details faithful to the recipe text.
                - Do not invent ingredients, quantities, timings, or temperatures the recipe doesn't state.
                - Keep the recipe's meaning unchanged.
                - Return ONLY JSON, matching the OUTPUT shape exactly.
                """;
    }

    // --- vocabulary rendering (once, at startup — the catalog is static) ---

    private static String buildVocabularyBlock(RecipeStepVocabularyProvider vocabulary) {
        StringBuilder out = new StringBuilder("VOCABULARY\n\n");

        out.append("""
                ACTIONS — one per line: id | on | fields | meaning
                  on: I = ingredients, S = subprocess outputs, "-" = no target, 1I = at most one ingredient,
                      a trailing "!" = at least one target required.
                  fields: the ONLY fields this action may use. "!" = required, "*" = strongly recommended when the
                      recipe states it. preparationStyle{...} lists the allowed preparation style sets;
                      temperature(...) gives what the temperature measures.
                """);
        Map<String, List<ActionDefinition>> actionsByCategory = groupBy(vocabulary.actions(), ActionDefinition::category);
        for (RecipeStepVocabularyProvider.CategoryDefinition category : vocabulary.actionCategories()) {
            List<ActionDefinition> inCategory = actionsByCategory.getOrDefault(category.id(), List.of());
            if (inCategory.isEmpty()) continue;
            out.append(category.label()).append(":\n");
            for (ActionDefinition action : inCategory) {
                out.append("- ").append(action.id())
                        .append(" | ").append(describeTargets(action))
                        .append(" | ").append(describeFields(action))
                        .append(" | ").append(action.description())
                        .append('\n');
            }
        }

        out.append("\nPREPARATION STYLE SETS (preparationStyle must be a style id from one of the action's sets, or \"custom\"):\n");
        vocabulary.preparationStyleSets().forEach(set ->
                out.append("- ").append(set.id()).append(": ").append(String.join(", ", set.styles())).append('\n'));

        out.append("\nINGREDIENTS — id (default unit) [aliases]:\n");
        Map<String, List<IngredientDefinition>> ingredientsByCategory = groupBy(vocabulary.ingredients(), IngredientDefinition::category);
        for (RecipeStepVocabularyProvider.CategoryDefinition category : vocabulary.ingredientCategories()) {
            List<IngredientDefinition> inCategory = ingredientsByCategory.getOrDefault(category.id(), List.of());
            if (inCategory.isEmpty()) continue;
            out.append(category.label()).append(": ")
                    .append(inCategory.stream().map(RecipeProcessGenerationPromptBuilder::describeIngredient).collect(Collectors.joining(", ")))
                    .append('\n');
        }

        out.append("\nUNITS:\n");
        Map<String, List<UnitDefinition>> unitsByCategory = groupBy(vocabulary.units(), UnitDefinition::category);
        for (RecipeStepVocabularyProvider.CategoryDefinition category : vocabulary.unitCategories()) {
            List<UnitDefinition> inCategory = unitsByCategory.getOrDefault(category.id(), List.of());
            if (inCategory.isEmpty()) continue;
            boolean nonNumeric = inCategory.stream().noneMatch(UnitDefinition::quantifiable);
            out.append("- ").append(category.label()).append(nonNumeric ? " (quantity must be null)" : "").append(": ")
                    .append(inCategory.stream().map(UnitDefinition::id).collect(Collectors.joining(", ")))
                    .append('\n');
        }

        out.append("\nflameLevel (heat level): ").append(String.join(", ", vocabulary.flameLevelIdSet())).append('\n');
        out.append("temperatureUnit: ").append(String.join(", ", vocabulary.temperatureUnitIds())).append('\n');
        out.append("temperature contexts: ").append(vocabulary.temperatureContextLabels().entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("; "))).append('\n');
        out.append("duration / repeatInterval units: ").append(String.join(", ", vocabulary.durationUnitIds())).append('\n');
        return out.toString();
    }

    private static String describeTargets(ActionDefinition action) {
        String targets = switch (action.actionOn()) {
            case INGREDIENT -> action.multipleIngredients() ? "I" : "1I";
            case PROCESS -> "S";
            case BOTH -> (action.multipleIngredients() ? "I" : "1I") + "+S";
            case NONE -> "-";
        };
        return action.actionOnRequired() ? targets + "!" : targets;
    }

    private static String describeFields(ActionDefinition action) {
        List<String> parts = new ArrayList<>();
        action.fields().forEach((key, requirement) -> {
            String name = FIELD_OUTPUT_NAMES.get(key);
            if (name == null) return; // unitId travels with quantity
            StringBuilder part = new StringBuilder(name);
            if (FIELD_PREPARATION_STYLE.equals(key)) {
                part.append('{').append(String.join(",", action.preparationStyleSets())).append('}');
            }
            if (FIELD_TEMPERATURE.equals(key) && action.temperatureContext() != null) {
                part.append('(').append(action.temperatureContext()).append(')');
            }
            if (requirement == FieldRequirement.REQUIRED) part.append('!');
            if (requirement == FieldRequirement.RECOMMENDED) part.append('*');
            parts.add(part.toString());
        });
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static String describeIngredient(IngredientDefinition ingredient) {
        StringBuilder entry = new StringBuilder(ingredient.id()).append(" (").append(ingredient.defaultUnit()).append(')');
        if (!ingredient.aliases().isEmpty()) {
            entry.append(" [")
                    .append(String.join(", ", ingredient.aliases().subList(0, Math.min(MAX_INGREDIENT_ALIASES, ingredient.aliases().size()))))
                    .append(']');
        }
        return entry.toString();
    }

    private static <T> Map<String, List<T>> groupBy(List<T> entries, java.util.function.Function<T, String> key) {
        Map<String, List<T>> grouped = new LinkedHashMap<>();
        entries.forEach(entry -> grouped.computeIfAbsent(key.apply(entry), k -> new ArrayList<>()).add(entry));
        return grouped;
    }
}
