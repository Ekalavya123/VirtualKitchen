package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.service.DbIngredientVocabularySource;
import com.processVisualisation.virtualKitchen.ai.service.RecipeProcessGenerationPromptBuilder;
import com.processVisualisation.virtualKitchen.ai.service.RecipeProcessOutputNormalizer;
import com.processVisualisation.virtualKitchen.ai.service.RecipeProcessOutputSchema;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.catalog.IngredientCatalogChangedEvent;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The recipe vocabulary's ingredients coming from the database catalog (ids = database ids). */
class DbIngredientVocabularyTest {

    private final List<Ingredient> stored = new ArrayList<>();
    private final IngredientRepository repository = mock(IngredientRepository.class);
    private final RecipeStepVocabularyProvider vocabulary;

    DbIngredientVocabularyTest() {
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(stored));
        stored.add(ingredient(42L, "Onion", "onion", List.of("piece", "g")));
        vocabulary = new RecipeStepVocabularyProvider(new DbIngredientVocabularySource(repository));
    }

    @Test
    void usesDatabaseIdsAndResolvesNamesAliasesAndOldSlugs() {
        assertTrue(vocabulary.ingredient("42").isPresent());
        assertEquals("Onion", vocabulary.ingredientLabel("42"));
        assertEquals("piece", vocabulary.ingredient("42").orElseThrow().defaultUnit());
        assertTrue(vocabulary.ingredient("custom").isPresent(), "custom stays available");
        assertFalse(vocabulary.ingredient("onion").isPresent(), "a slug is not an id any more");
        assertEquals("42", vocabulary.resolveIngredientId("onion").orElseThrow());
        assertEquals("42", vocabulary.resolveIngredientId("Onion").orElseThrow());
        assertEquals("42", vocabulary.resolveIngredientId("pyaaz").orElseThrow());
    }

    @Test
    void reloadsWhenTheCatalogChanges() {
        long version = vocabulary.ingredientCatalogVersion();
        assertTrue(vocabulary.ingredient("42").isPresent()); // loads (and caches) the catalog
        stored.add(ingredient(43L, "Tomato", "tomato", List.of("piece")));
        assertFalse(vocabulary.ingredient("43").isPresent(), "cached until told otherwise");

        vocabulary.onIngredientCatalogChanged(new IngredientCatalogChangedEvent("create"));

        assertTrue(vocabulary.ingredient("43").isPresent());
        assertTrue(vocabulary.ingredientCatalogVersion() > version);
    }

    @Test
    void promptListsDatabaseIdsAndIsRebuiltAfterAChange() {
        RecipeProcessGenerationPromptBuilder prompts = new RecipeProcessGenerationPromptBuilder(vocabulary, new RecipeProcessOutputSchema(vocabulary));
        assertTrue(prompts.buildSystemPrompt().contains("42=Onion (piece)"));
        stored.add(ingredient(43L, "Tomato", "tomato", List.of("piece")));
        vocabulary.refreshIngredients();
        assertTrue(prompts.buildSystemPrompt().contains("43=Tomato (piece)"));
    }

    @Test
    void normalizerMapsAnIngredientNamedByTheModelOntoItsId() {
        RecipeProcessOutputNormalizer normalizer = new RecipeProcessOutputNormalizer(vocabulary);
        assertEquals("42", normalizer.resolveIngredientId("onion"));
        assertEquals("42", normalizer.resolveIngredientId("42"));
        assertEquals("custom", normalizer.resolveIngredientId("custom"));
        assertEquals("unobtainium", normalizer.resolveIngredientId("unobtainium"), "left for the validator to report");
    }

    private static Ingredient ingredient(long id, String name, String slug, List<String> units) {
        Ingredient ingredient = new Ingredient();
        ingredient.setId(id);
        ingredient.setName(name);
        ingredient.setCatalogSlug(slug);
        ingredient.setCategory("vegetables");
        ingredient.setRecipeUnits(units);
        ingredient.setAliases(List.of("pyaaz"));
        ingredient.setDefaultUnit(UnitType.COUNT);
        return ingredient;
    }
}
