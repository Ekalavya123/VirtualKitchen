package com.processVisualisation.virtualKitchen.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the system and user prompts used to convert free-form recipe text
 * into a semantic Process structure (MAIN process + subprocesses, each a
 * flat list of STEP/CONDITION nodes) — mirroring {@link AIRecipeFlowPromptBuilder}
 * but targeting the new Process model's JSON shape instead of the legacy
 * flat {steps, edges} flow graph. No React Flow field (id, position,
 * dimensions, handles) is ever requested — the application generates those
 * when converting the result into the Process working snapshot.
 */
@Component
public class ProcessGenerationPromptBuilder {

    /** Process's own fixed unit enum (UnitType) — distinct from the legacy flow model's broader unit catalog, so not sourced from {@link StepVocabularyProvider}. */
    private static final String UNIT_IDS = "COUNT|GRAM|KG|ML|LITER";

    private final StepVocabularyProvider stepVocabularyProvider;

    public ProcessGenerationPromptBuilder(StepVocabularyProvider stepVocabularyProvider) {
        this.stepVocabularyProvider = stepVocabularyProvider;
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
                  "action": "",
                  "actionOn": {
                    "ingredients": [
                      { "ingredientId": "", "quantity": 1, "unit": "", "preparationStyle": "", "customIngredientName": "" }
                    ],
                    "processes": []
                  },
                  "actionDescription": "",
                  "expectedOutput": "",
                  "temperature": "",
                  "flameLevel": "",
                  "duration": ""
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

                ACTION ON — INGREDIENT-SPECIFIC PROPERTIES
                - actionOn.ingredients is a list; a step's action can apply to multiple ingredients at once
                  (e.g. Cut applied to onion, tomato, and chilli separately).
                - EACH ingredient entry has its OWN quantity/unit/preparationStyle. Never put quantity, unit, or
                  preparation style at the step level — only inside that ingredient's own entry.
                - quantity defaults to 1 when the recipe doesn't state an amount.
                - unit should reflect what the recipe states; when not stated, leave it as the ingredient's
                  typical default rather than guessing an unusual unit.
                - preparationStyle: only include it when the action/ingredient combination genuinely involves a
                  preparation style (e.g. Cut/Chop/Slice/Dice) — omit it entirely (or leave it blank) for actions
                  like Boil, Heat, Season, Mix where it does not apply. Never invent a preparation style the
                  recipe doesn't imply.
                - actionOn.processes lists the "ref" of any subprocess this step's action uses the result of
                  (e.g. a "cook" step referencing "marinate_chicken"). Leave it empty when the step doesn't use
                  a subprocess's output.
                - A step can have ingredients, subprocess references, both, or (rarely) neither.

                CORE STEP FIELDS
                - actionDescription: a short natural-language description of what this step does. Always include it.
                - expectedOutput: the expected result/state after this step. Include it whenever it can be
                  reasonably inferred from the recipe (e.g. "onions turn golden brown"); leave it as an empty
                  string only when the recipe gives no basis for it. Do not invent specific, unstated outcomes.

                ADVANCED PROPERTIES (OPTIONAL)
                - temperature/flameLevel/duration are optional. Only include a value when the recipe explicitly
                  states it or it can be determined reliably (e.g. "fry on medium flame for 5 minutes"). Leave
                  them as empty strings otherwise — never invent a specific temperature, flame level, or duration.

                VOCABULARY
                action must be one of:
                [%s]

                ingredientId must be one of:
                [%s]
                Use "customIngredientName" with ingredientId "custom" only when no listed ingredient reasonably
                matches — never silently map an unlisted ingredient onto the wrong catalog entry.

                unit must be one of:
                [%s]

                preparationStyle, when included, must be one of:
                [%s]

                flameLevel, when included, must be one of:
                [%s]

                GENERAL RULES
                - Keep quantities, units, durations, temperatures, and cooking details faithful to the recipe text.
                - Do not invent ingredients, quantities, timings, or temperatures the recipe doesn't state.
                - Keep the recipe's meaning unchanged.
                - Return ONLY JSON, matching the OUTPUT shape exactly.
                """.formatted(
                stepVocabularyProvider.actionIds(),
                stepVocabularyProvider.ingredientIds(),
                UNIT_IDS,
                stepVocabularyProvider.preparationStyleIds(),
                stepVocabularyProvider.flameLevelIds()
        );
    }
}
