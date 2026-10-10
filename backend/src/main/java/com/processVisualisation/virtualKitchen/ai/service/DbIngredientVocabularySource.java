package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider.IngredientDefinition;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the recipe vocabulary's ingredient list from the database ingredient catalog — the single
 * catalog the recipe editor, the shop and kitchen inventory all share. An ingredient's vocabulary id
 * is its database id as a string (what recipe steps store); its old catalog slug is kept as an alias
 * so text that still names it (an AI response, an old draft) resolves to the right ingredient.
 */
@Component
public class DbIngredientVocabularySource implements IngredientVocabularySource {

    private static final IngredientDefinition CUSTOM = new IngredientDefinition(
            RecipeStepVocabularyProvider.CUSTOM_ID, "Custom Ingredient", "other", "piece", List.of(), List.of(), null);

    private final IngredientRepository ingredientRepository;

    public DbIngredientVocabularySource(IngredientRepository ingredientRepository) {
        this.ingredientRepository = ingredientRepository;
    }

    @Override
    public List<IngredientDefinition> loadIngredients() {
        List<IngredientDefinition> definitions = new ArrayList<>();
        ingredientRepository.findAll().stream()
                .filter(ingredient -> ingredient.getId() != null && ingredient.getName() != null)
                .sorted(Comparator.comparing(Ingredient::getId))
                .map(DbIngredientVocabularySource::toDefinition)
                .forEach(definitions::add);
        definitions.add(CUSTOM);
        return definitions;
    }

    static IngredientDefinition toDefinition(Ingredient ingredient) {
        List<String> units = ingredient.getRecipeUnits() == null ? List.of() : List.copyOf(ingredient.getRecipeUnits());
        String defaultUnit = !units.isEmpty() ? units.get(0) : recipeUnitOf(ingredient.getDefaultUnit());
        List<String> aliases = new ArrayList<>();
        if (ingredient.getCatalogSlug() != null) aliases.add(ingredient.getCatalogSlug());
        if (ingredient.getAliases() != null) aliases.addAll(ingredient.getAliases());
        return new IngredientDefinition(
                String.valueOf(ingredient.getId()),
                ingredient.getName(),
                ingredient.getCategory() == null ? "other" : ingredient.getCategory(),
                defaultUnit,
                units,
                List.copyOf(aliases),
                ingredient.getPreparationStyleSets() == null ? null : List.copyOf(ingredient.getPreparationStyleSets()));
    }

    /** The recipe unit id matching a shop/inventory unit. */
    static String recipeUnitOf(UnitType unit) {
        if (unit == null) return "piece";
        return switch (unit) {
            case KG -> "kg";
            case GRAM -> "g";
            case LITER -> "l";
            case ML -> "ml";
            case COUNT -> "piece";
        };
    }
}
