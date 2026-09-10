package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.service.StepVocabularyProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepVocabularyProviderTest {

    @Test
    void loadsVocabularyFromTheSharedCatalogFile() {
        StepVocabularyProvider provider = new StepVocabularyProvider();

        List<String> actions = Arrays.asList(provider.actionIds().split("\\|"));
        List<String> ingredients = Arrays.asList(provider.ingredientIds().split("\\|"));
        List<String> units = Arrays.asList(provider.unitIds().split("\\|"));
        List<String> preparationStyles = Arrays.asList(provider.preparationStyleIds().split("\\|"));
        List<String> flameLevels = Arrays.asList(provider.flameLevelIds().split("\\|"));

        assertTrue(actions.contains("mix"));
        assertTrue(actions.contains("custom"));

        // Regression test for the drift bug: the backend's hardcoded INGREDIENT_IDS string was
        // missing "paneer" even though it existed in the frontend catalog. Both sides now read
        // the identical file, so this can't happen again.
        assertTrue(ingredients.contains("paneer"), "ingredient vocabulary must include every frontend catalog entry");
        assertTrue(ingredients.contains("custom"));

        assertEquals(10, units.size());
        assertEquals(8, preparationStyles.size());
        assertEquals(4, flameLevels.size());
    }
}
