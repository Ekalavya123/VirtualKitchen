package com.processVisualisation.virtualKitchen.store.catalog;

import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IngredientCatalogSeederTest {

    private final Map<Long, Ingredient> stored = new LinkedHashMap<>();
    private final IngredientRepository repository = mock(IngredientRepository.class);
    private final SequenceGeneratorService sequences = mock(SequenceGeneratorService.class);
    private IngredientCatalogSeeder seeder;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(100);
        when(sequences.generateSequence(anyString())).thenAnswer(invocation -> ids.incrementAndGet());
        when(repository.findAll()).thenAnswer(invocation -> new ArrayList<>(stored.values()));
        when(repository.save(any(Ingredient.class))).thenAnswer(invocation -> {
            Ingredient ingredient = invocation.getArgument(0);
            stored.put(ingredient.getId(), ingredient);
            return ingredient;
        });
        seeder = new IngredientCatalogSeeder(repository, sequences, mock(ApplicationEventPublisher.class), true);
    }

    @Test
    void seedsTheWholeCatalogAndLinksExistingIngredients() {
        Ingredient oliveOil = new Ingredient();
        oliveOil.setId(3L);
        oliveOil.setName("Olive Oil");
        oliveOil.setDefaultUnit(UnitType.LITER);
        oliveOil.setImageUrl("https://example.invalid/olive-oil.png");
        stored.put(3L, oliveOil);

        IngredientCatalogSeeder.SeedResult result = seeder.seed();

        assertTrue(result.created() > 100, "every catalog ingredient becomes a database ingredient");
        assertEquals(1, result.linked());
        Ingredient linked = stored.get(3L);
        assertEquals("olive-oil", linked.getCatalogSlug());
        assertEquals(UnitType.LITER, linked.getDefaultUnit(), "existing values are kept");
        assertEquals("https://example.invalid/olive-oil.png", linked.getImageUrl());
        assertTrue(stored.values().stream().noneMatch(ingredient -> "custom".equals(ingredient.getCatalogSlug())));

        Ingredient onion = stored.values().stream().filter(i -> "onion".equals(i.getCatalogSlug())).findFirst().orElseThrow();
        assertEquals("Onion", onion.getName());
        assertTrue(onion.getRecipeUnits().size() > 0);
        assertEquals(UnitType.COUNT, onion.getDefaultUnit(), "sold by the piece, like its recipe default");
    }

    @Test
    void isIdempotent() {
        seeder.seed();
        int count = stored.size();
        IngredientCatalogSeeder.SeedResult again = seeder.seed();
        assertEquals(0, again.created());
        assertEquals(0, again.linked());
        assertEquals(0, again.updated());
        assertEquals(count, stored.size());
    }

    @Test
    void neverOverwritesFieldsEditedInTheDatabase() {
        seeder.seed();
        Ingredient onion = stored.values().stream().filter(i -> "onion".equals(i.getCatalogSlug())).findFirst().orElseThrow();
        onion.setCategory("edited");
        onion.setRecipeUnits(new ArrayList<>(List.of("g")));
        seeder.seed();
        assertEquals("edited", stored.get(onion.getId()).getCategory());
        assertEquals(List.of("g"), stored.get(onion.getId()).getRecipeUnits());
    }

    @Test
    void derivesTheShopUnitFromTheRecipeDefaultUnit() {
        Map<String, String> categories = Map.of("g", "weight", "kg", "weight", "ml", "volume", "tbsp", "spoon", "l", "volume", "clove", "count");
        assertEquals(UnitType.GRAM, IngredientCatalogSeeder.shopUnitFor("g", categories));
        assertEquals(UnitType.KG, IngredientCatalogSeeder.shopUnitFor("kg", categories));
        assertEquals(UnitType.ML, IngredientCatalogSeeder.shopUnitFor("tbsp", categories));
        assertEquals(UnitType.LITER, IngredientCatalogSeeder.shopUnitFor("l", categories));
        assertEquals(UnitType.COUNT, IngredientCatalogSeeder.shopUnitFor("piece", categories));
        assertEquals(UnitType.GRAM, IngredientCatalogSeeder.shopUnitFor("clove", categories));
    }
}
