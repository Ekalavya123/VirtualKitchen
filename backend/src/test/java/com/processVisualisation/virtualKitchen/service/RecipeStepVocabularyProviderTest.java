package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.ActionTarget;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.FieldRequirement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeStepVocabularyProviderTest {

    private final RecipeStepVocabularyProvider provider = new RecipeStepVocabularyProvider();

    @Test
    void loadsVocabularyFromTheSharedCatalogFile() {
        List<String> actions = Arrays.asList(provider.actionIds().split("\\|"));
        List<String> ingredients = Arrays.asList(provider.ingredientIds().split("\\|"));

        assertTrue(actions.contains("mix"));
        assertTrue(actions.contains("custom"));

        // Regression test for the drift bug: the backend's hardcoded INGREDIENT_IDS string was
        // missing "paneer" even though it existed in the frontend catalog. Both sides now read
        // the identical file, so this can't happen again.
        assertTrue(ingredients.contains("paneer"), "ingredient vocabulary must include every frontend catalog entry");
        assertTrue(ingredients.contains("custom"));
    }

    @Test
    void keepsEveryPocCatalogIdForBackwardCompatibility() {
        assertContainsAll(provider.actionIds(), "add remove pour season cut chop slice dice heat boil fry bake stir mix whisk wait rest serve garnish custom");
        assertContainsAll(provider.ingredientIds(), "water oil salt sugar rice onion tomato garlic ginger chili potato carrot capsicum egg milk butter chicken paneer custom");
        assertContainsAll(provider.unitIds(), "ml l cup g kg piece tsp tbsp pinch custom");
        assertContainsAll(provider.preparationStyleIds(), "fine medium large thin-slice thick-slice julienne rough-chop custom");
        assertContainsAll(provider.flameLevelIds(), "low medium high custom");
    }

    @Test
    void mapsLegacyProcessUnitTypesOntoCatalogUnits() {
        assertEquals(Optional.of("piece"), provider.resolveUnitId("COUNT"));
        assertEquals(Optional.of("g"), provider.resolveUnitId("GRAM"));
        assertEquals(Optional.of("kg"), provider.resolveUnitId("KG"));
        assertEquals(Optional.of("ml"), provider.resolveUnitId("ML"));
        assertEquals(Optional.of("l"), provider.resolveUnitId("LITER"));
        assertEquals("g", provider.unitLabel("GRAM"));
        assertEquals("", provider.unitLabel("COUNT"), "a plain piece count has no unit label");
        assertEquals("tbsp", provider.unitLabel("tbsp"));
    }

    @Test
    void exposesPerActionRules() {
        RecipeStepVocabularyProvider.ActionDefinition bake = provider.action("bake").orElseThrow();
        assertEquals(ActionTarget.BOTH, bake.actionOn());
        assertEquals(FieldRequirement.RECOMMENDED, bake.fieldRequirement("temperature"));
        assertEquals("oven", bake.temperatureContext());

        RecipeStepVocabularyProvider.ActionDefinition chop = provider.action("chop").orElseThrow();
        assertEquals(ActionTarget.INGREDIENT, chop.actionOn());
        assertFalse(chop.hasField("duration"));

        assertEquals(ActionTarget.NONE, provider.action("preheat").orElseThrow().actionOn());
    }

    @Test
    void preparationStylesAreFilteredByActionAndIngredient() {
        assertTrue(provider.allowedPreparationStyles("slice").contains("thin-slice"));
        assertFalse(provider.allowedPreparationStyles("slice").contains("cubed"));
        assertTrue(provider.allowedPreparationStyles("stir").isEmpty(), "stir never takes a preparation style");

        assertTrue(provider.allowedPreparationStyles("cut", "onion").contains("finely-diced"));
        assertTrue(provider.allowedPreparationStyles("cut", "water").isEmpty(), "water can't be cut into anything");
    }

    @Test
    void resolvesAliasesForInputOnly() {
        assertEquals(Optional.of("cilantro"), provider.resolveIngredientId("Dhania"));
        assertEquals(Optional.of("chickpea-flour"), provider.resolveIngredientId("besan"));
        assertEquals(Optional.of("wash"), provider.resolveActionId("rinse"));
        assertEquals(Optional.of("to-taste"), provider.resolveUnitId("to taste"));
        assertFalse(provider.isQuantifiableUnit("to-taste"));
    }

    private static void assertContainsAll(String pipeJoined, String spaceSeparatedIds) {
        List<String> ids = Arrays.asList(pipeJoined.split("\\|"));
        for (String id : spaceSeparatedIds.split(" ")) {
            assertTrue(ids.contains(id), "missing legacy id: " + id);
        }
    }
}
