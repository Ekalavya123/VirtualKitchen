package com.processVisualisation.virtualKitchen.ai.service;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AIRecipeFlowPromptBuilder {

    private static final String ACTION_IDS = "add|remove|pour|season|cut|chop|slice|dice|heat|boil|fry|bake|stir|mix|whisk|wait|rest|serve|garnish";
    private static final String INGREDIENT_IDS = "water|oil|salt|sugar|rice|onion|tomato|garlic|ginger|chili|potato|carrot|capsicum|egg|milk|butter|chicken|custom";
    private static final String UNIT_IDS = "ml|l|cup|g|kg|piece|tsp|tbsp|pinch|custom";
    private static final String PREPARATION_STYLE_IDS = "fine|medium|large|thin-slice|thick-slice|julienne|rough-chop|custom";
    private static final String FLAME_LEVEL_IDS = "low|medium|high|custom";

    public String buildSystemPrompt() {
        return "Generate strict JSON only for a recipe execution model. "
                + "No markdown, prose, code fences, or UI graph fields.";
    }

    public String buildInitialPrompt(String recipeText) {
        return buildSchemaAndRulesBlock() + "\n"
                + "Input recipe text:\n"
                + recipeText + "\n\n"
                + "Now generate a valid JSON object only.";
    }

    public String buildRetryPrompt(String recipeText, String previousOutput, List<String> validationErrors) {
        String errors = String.join("; ", validationErrors);
        return buildSchemaAndRulesBlock() + "\n"
                + "Input recipe text:\n"
                + recipeText + "\n\n"
                + "Validation errors that must be fixed:\n"
                + errors + "\n\n"
                + "Return corrected JSON only.";
    }

    private String buildSchemaAndRulesBlock() {
                return """
                                Return ONLY valid JSON.

                                The JSON must contain exactly:
                                {
                                    "steps": [],
                                    "edges": []
                                }

                                STEP NODE SCHEMA
                                Each item in "steps" must be:
                                {
                                    "id": "unique id",
                                    "nodeType": "recipeStep|condition|parallelStart|parallelEnd",
                                    "data": { ... }
                                }

                                NODE TYPE: recipeStep
                                Use for normal cooking actions (add, mix, boil, wait, serve, etc.).
                                Data schema:
                                {
                                    "action": "",
                                    "ingredientId": "",
                                    "quantity": "",
                                    "unit": "",
                                    "style": "",
                                    "duration": "",
                                    "flame": "",
                                    "temperature": "",
                                    "notes": ""
                                }

                                Controlled vocabularies for recipeStep:
                                - action in [%s]
                                - ingredientId in [%s] (or "custom")
                                - unit in [%s]
                                - style in [%s]
                                - flame in [%s]

                                NODE TYPE: condition
                                Use when text contains decisions like:
                                - if, else, otherwise, until, unless, check, verify, repeat until, when

                                Data schema:
                                {
                                    "title": "condition question",
                                    "notes": "",
                                    "expectedResult": "success"
                                }

                                Condition behavior:
                                - condition nodes should branch using YES/NO labeled edges.

                                NODE TYPE: parallelStart
                                Use when text indicates parallel work like:
                                - meanwhile, while, simultaneously, at the same time, in another pan

                                Data schema:
                                {
                                    "title": ""
                                }

                                NODE TYPE: parallelEnd
                                Use to merge/synchronize previously opened parallel branches.

                                Data schema:
                                {
                                    "title": ""
                                }

                                EDGE SCHEMA
                                Each item in "edges" must be:
                                {
                                    "from": "source step id",
                                    "to": "target step id",
                                    "label": ""
                                }

                                Edge label rules:
                                - For condition outgoing branches, use label "YES" or "NO".
                                - For non-condition edges, label may be empty string.

                                Global rules:
                                - step ids must be unique
                                - every edge must reference existing step ids
                                - every step must include nodeType and data
                                - every parallelStart must eventually connect to a parallelEnd
                                - keep content semantically complete for cooking execution
                                - no UI graph fields (no position, x, y, width, height, style, handles, react-flow props)
                                - unknown optional values should be empty string

                                Return ONLY JSON.
                                """.formatted(
                                ACTION_IDS,
                                INGREDIENT_IDS,
                                UNIT_IDS,
                                PREPARATION_STYLE_IDS,
                                FLAME_LEVEL_IDS
                );
    }
}
