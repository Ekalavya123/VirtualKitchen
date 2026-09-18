package com.processVisualisation.virtualKitchen.recipe.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms the field defaults new Phase 1 fields carry on a freshly
 * constructed {@link RecipeTemplate} — the same defaults Spring Data
 * MongoDB applies when unmarshalling a pre-existing document that has none
 * of these keys, which is what the recipe/process migration script relies
 * on to avoid needing to rewrite every existing document.
 */
class RecipeTemplateDefaultsTest {

    @Test
    void newRecipeTemplate_hasEmptyIngredientsAndNoNutritionOrMainProcess() {
        RecipeTemplate template = new RecipeTemplate();

        assertNotNull(template.getIngredients());
        assertTrue(template.getIngredients().isEmpty());
        assertNull(template.getNutrition());
        assertNull(template.getMainProcessId());
    }
}
