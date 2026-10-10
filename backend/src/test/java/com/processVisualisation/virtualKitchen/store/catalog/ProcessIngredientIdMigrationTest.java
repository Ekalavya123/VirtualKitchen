package com.processVisualisation.virtualKitchen.store.catalog;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessIngredientIdMigrationTest {

    private final Function<String, Ingredient> catalog = slug -> {
        if (!"onion".equals(slug)) return null;
        Ingredient onion = new Ingredient();
        onion.setId(42L);
        return onion;
    };

    @Test
    void rewritesSlugsToDatabaseIdsAndKeepsUnknownOnesAsCustom() {
        Map<String, Object> onion = line("onion", null);
        Map<String, Object> unknown = line("dragon-fruit", null);
        Map<String, Object> custom = line("custom", "Grandma's spice mix");
        Map<String, Object> migrated = line("17", null);
        List<Process.ProcessNode> nodes = List.of(step(onion, unknown, custom, migrated));

        assertTrue(ProcessIngredientIdMigration.migrateNodes(nodes, catalog));

        assertEquals("42", onion.get("ingredientId"));
        assertEquals("custom", unknown.get("ingredientId"));
        assertEquals("Dragon Fruit", unknown.get("customIngredientName"));
        assertEquals("custom", custom.get("ingredientId"));
        assertEquals("Grandma's spice mix", custom.get("customIngredientName"));
        assertEquals("17", migrated.get("ingredientId"));
    }

    @Test
    void isIdempotent() {
        List<Process.ProcessNode> nodes = List.of(step(line("onion", null)));
        assertTrue(ProcessIngredientIdMigration.migrateNodes(nodes, catalog));
        assertFalse(ProcessIngredientIdMigration.migrateNodes(nodes, catalog), "already-migrated data is left alone");
    }

    private static Map<String, Object> line(String ingredientId, String customName) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("ingredientId", ingredientId);
        line.put("quantity", 1);
        line.put("unit", "piece");
        if (customName != null) line.put("customIngredientName", customName);
        return line;
    }

    @SafeVarargs
    private static Process.ProcessNode step(Map<String, Object>... lines) {
        Map<String, Object> actionOn = new LinkedHashMap<>();
        actionOn.put("ingredients", new ArrayList<>(List.of(lines)));
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("actionOn", actionOn);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("step", step);
        Process.ProcessNode node = new Process.ProcessNode();
        node.setId("n1");
        node.setKind(ProcessNodeKind.STEP);
        node.setData(data);
        return node;
    }
}
