package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One validated change to an existing process, applied by the frontend in list order. Node references are real
 * node ids, the {@code ref} of a node added by an earlier operation in the same list, or {@link #START}.
 * <ul>
 *   <li>ADD_STEP / ADD_CONDITION: insert {@code step} (a full node, nodeType STEP/CONDITION) right after
 *       {@code after}; later operations refer to it by {@code ref}.</li>
 *   <li>UPDATE_STEP / UPDATE_CONDITION: on {@code target}, overwrite every non-null field of {@code step}
 *       (actionOn.steps/processes replace the whole list when non-null; actionOn.ingredients is ignored) and reset
 *       the fields named in {@code clear}.</li>
 *   <li>ADD_INGREDIENT: append {@code ingredient} to {@code target}.</li>
 *   <li>UPDATE_INGREDIENT: merge the non-null fields of {@code ingredient} into {@code target}'s ingredient
 *       {@code ingredientId}.</li>
 *   <li>REMOVE_INGREDIENT: drop {@code target}'s ingredient {@code ingredientId}.</li>
 *   <li>REPLACE_INGREDIENT: swap {@code target}'s ingredient {@code ingredientId} for {@code ingredient}, keeping
 *       the old quantity/unit/preparationStyle wherever {@code ingredient} leaves them null.</li>
 *   <li>DELETE_NODE: remove {@code target}, reconnecting its predecessors to its successor.</li>
 *   <li>MOVE_NODE: detach {@code target} the same way and re-insert it right after {@code after}.</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessEditOperationDTO {

    /** {@code after} value meaning "before the first node". */
    public static final String START = "START";

    private String op;
    private String target;
    private String after;
    /** Temporary reference of the node an ADD_* operation creates. */
    private String ref;
    private GeneratedRecipeStepDTO step;
    /** UPDATE_STEP only: field names to reset (see RecipeProcessEditOutputSchema.CLEARABLE_FIELDS). */
    private List<String> clear;
    private GeneratedActionOnIngredientDTO ingredient;
    private String ingredientId;
}
