package com.processVisualisation.virtualKitchen.store.model;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document representing an ingredient in the store catalog, with
 * a default {@link UnitType} used when quantities are not otherwise
 * specified. Referenced by identifier from {@link Inventory} and
 * {@link ItemCost} via {@link ItemType#INGREDIENT}, and from a recipe's
 * ingredient list via
 * {@code com.processVisualisation.virtualKitchen.recipe.model.RecipeIngredient}.
 * {@link #imageUrl} lives here (not on the per-recipe reference) so the same
 * ingredient's image is reused across every recipe rather than duplicated.
 */
@Data
@Document(collection = "ingredients")
public class Ingredient {

    public static final String SEQUENCE_NAME = "ingredients_sequence";

    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    private String description;

    private UnitType defaultUnit;

    private String imageUrl;

    /**
     * The id this ingredient had in the static step catalog (e.g. {@code "onion"}) before the
     * database became the ingredient catalog. Used only to seed the catalog and to migrate data
     * saved under the old slug ids — never to match recipes against inventory (that is by {@link #id}).
     */
    @Indexed(unique = true, sparse = true)
    private String catalogSlug;

    /** Recipe-editor category id (see {@code ingredientCategories} in stepCatalogs.data.json). */
    private String category;

    private String icon;

    /** Search/interpretation only. */
    private List<String> aliases = new ArrayList<>();

    /** Recipe unit ids offered for this ingredient: the recipe default first, then alternatives. */
    private List<String> recipeUnits = new ArrayList<>();

    /** Overrides the category's preparation style sets when present. */
    private List<String> preparationStyleSets;

    /** Grams per millilitre, overriding the unit-conversions.json default for this ingredient. */
    private Double densityGPerMl;

    /** Grams per recipe unit (e.g. {@code {"clove": 5}}), overriding the unit-conversions.json defaults. */
    private Map<String, Double> unitWeightsG;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}