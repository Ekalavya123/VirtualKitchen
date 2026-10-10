package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.IngredientDefinition;

import java.util.List;

/**
 * Where {@link RecipeStepVocabularyProvider} gets its ingredient list from. In the running
 * application that is the database ingredient catalog ({@link DbIngredientVocabularySource}); the
 * provider's no-arg constructor (used by tests) reads the ingredient seed in stepCatalogs.data.json.
 */
public interface IngredientVocabularySource {

    /** Every ingredient, including the {@code "custom"} pseudo-ingredient, in display order. */
    List<IngredientDefinition> loadIngredients();
}
