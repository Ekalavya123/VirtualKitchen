package com.processVisualisation.virtualKitchen.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the system and user prompts used to convert free-form recipe text
 * into a structured, executable recipe flow (steps/edges JSON graph) via the
 * AI provider, including the controlled vocabularies (actions, ingredients,
 * units, etc.) and schema/rules the model must follow.
 */
@Component
public class AIRecipeFlowPromptBuilder {

    private static final String ACTION_IDS =
            "add|remove|pour|season|cut|chop|slice|dice|heat|boil|fry|bake|stir|mix|whisk|wait|rest|serve|garnish";

    private static final String INGREDIENT_IDS =
            "water|oil|salt|sugar|rice|onion|tomato|garlic|ginger|chili|potato|carrot|capsicum|egg|milk|butter|chicken|custom";

    private static final String UNIT_IDS =
            "ml|l|cup|g|kg|piece|tsp|tbsp|pinch|custom";

    private static final String PREPARATION_STYLE_IDS =
            "fine|medium|large|thin-slice|thick-slice|julienne|rough-chop|custom";

    private static final String FLAME_LEVEL_IDS =
            "low|medium|high|custom";


    /**
     * Builds the fixed system prompt instructing the AI model to convert
     * recipe text into a JSON cooking execution flow with no extra formatting.
     *
     * @return the system prompt text
     */
    public String buildSystemPrompt() {
        return """
                Convert recipe text into a cooking execution flow.

                Return ONLY valid JSON.
                Do not return markdown, explanations, comments, or code fences.
                Do not include UI or React Flow fields.
                """;
    }


    /**
     * Builds the initial user prompt for a first-pass flow generation
     * attempt, combining the schema/rules block with the raw recipe text.
     *
     * @param recipeText the free-form recipe text to convert
     * @return the composed initial prompt
     */
    public String buildInitialPrompt(String recipeText) {
        return buildSchemaAndRulesBlock()
                + "\nRECIPE:\n"
                + recipeText
                + "\n\nReturn the JSON flow only.";
    }


    /**
     * Builds a retry prompt asking the AI model to correct a previous
     * invalid flow generation attempt, including the prior output and the
     * validation errors it produced.
     *
     * @param recipeText the original free-form recipe text
     * @param previousOutput the raw JSON content returned by the prior attempt
     * @param validationErrors the validation errors found in the prior attempt
     * @return the composed retry prompt
     */
    public String buildRetryPrompt(
            String recipeText,
            String previousOutput,
            List<String> validationErrors) {

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
                Return exactly:
                {
                  "steps": [],
                  "edges": []
                }

                NODE TYPES

                1. recipeStep
                Use for a concrete cooking action.

                {
                  "id": "unique_id",
                  "nodeType": "recipeStep",
                  "data": {
                    "action": "",
                    "ingredientId": "",
                    "quantity": "",
                    "unit": "",
                    "style": "",
                    "duration": "",
                    "flame": "",
                    "temperature": "",
                    "description": ""
                  }
                }

                2. condition
                Use for decisions, checks, or repetition such as:
                if, otherwise, until, unless, check, verify, repeat until.

                {
                  "id": "unique_id",
                  "nodeType": "condition",
                  "data": {
                    "title": "",
                    "description": "",
                    "expectedResult": ""
                  }
                }

                CONDITION RULES
                - A condition represents a decision/check, not a cooking action.
                - Create YES and NO outgoing edges.
                - YES means the condition is satisfied.
                - NO means the condition is not satisfied.
                - If the recipe says "until X", create a condition checking X.
                - A repetition should connect the NO branch back to the action that must be repeated.
                - Do not create a condition for ordinary cooking instructions.

                NODE SPLITTING
                - Create a separate recipeStep for each meaningful cooking action.
                - Split multiple actions in one sentence into separate nodes.
                - Preserve the order of execution.
                - Do not unnecessarily split one atomic action into multiple nodes.

                EDGES

                Each edge:
                {
                  "from": "source_id",
                  "to": "target_id",
                  "label": ""
                }

                Edge rules:
                - Connect nodes according to actual cooking execution order.
                - Condition outgoing edges MUST use "YES" and "NO".
                - Normal edges use an empty label.
                - Every edge must reference existing node ids.
                - For repetition, the NO branch may point backward to the action being repeated.
                - Do not create unnecessary edges.

                RECIPE STEP FIELDS

                action must be one of:
                [%s]

                ingredientId must be one of:
                [%s]

                unit must be one of:
                [%s]

                style must be one of:
                [%s]

                flame must be one of:
                [%s]

                FIELD RULES
                - Use "custom" when a controlled value does not exist.
                - Use empty string when a value is unknown or not applicable.
                - Keep quantities, units, durations, temperatures and cooking details from the recipe.
                - Put additional cooking information in notes.
                - Do not invent ingredients, quantities, timings or temperatures.
                - Keep the recipe meaning unchanged.

                GRAPH RULES
                - Every node id must be unique.
                - Every edge must reference existing node ids.
                - The graph should represent the complete executable recipe.
                - Do not include position, width, height, handles, source, target, React Flow properties, or other UI fields.
                - Return ONLY JSON.
                """.formatted(
                ACTION_IDS,
                INGREDIENT_IDS,
                UNIT_IDS,
                PREPARATION_STYLE_IDS,
                FLAME_LEVEL_IDS
        );
    }
}
