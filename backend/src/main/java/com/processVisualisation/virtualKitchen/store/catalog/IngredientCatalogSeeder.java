package com.processVisualisation.virtualKitchen.store.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Makes the database the ingredient catalog: every ingredient in the seed list of
 * stepCatalogs.data.json becomes (or is linked to) an {@link Ingredient} document, so the recipe
 * editor, the shop and kitchen inventory all refer to one ingredient by one id.
 * <p>
 * Idempotent and non-destructive: an entry is matched to an existing document by catalog slug,
 * else by name/alias (linking e.g. an existing "Olive Oil"); only fields that are still empty are
 * filled, so edits made in the database are never overwritten. Runs before
 * {@link ProcessIngredientIdMigration}, which needs the slug → id mapping.
 */
@Component
@Order(1)
public class IngredientCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngredientCatalogSeeder.class);

    private final IngredientRepository ingredientRepository;
    private final SequenceGeneratorService sequenceGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final boolean enabled;

    public IngredientCatalogSeeder(
            IngredientRepository ingredientRepository,
            SequenceGeneratorService sequenceGenerator,
            ApplicationEventPublisher eventPublisher,
            @Value("${vk.catalog.seed-ingredients.enabled:true}") boolean enabled
    ) {
        this.ingredientRepository = ingredientRepository;
        this.sequenceGenerator = sequenceGenerator;
        this.eventPublisher = eventPublisher;
        this.enabled = enabled;
    }

    /** One ingredient entry of the seed list. */
    record SeedEntry(String slug, String name, String category, String icon, String defaultUnit,
                     List<String> units, List<String> aliases, List<String> preparationStyleSets) {}

    /** What one seeding pass did. */
    public record SeedResult(int created, int linked, int updated, int unchanged) {}

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            SeedResult result = seed();
            log.info("event=ingredient_catalog_seeded created={} linked={} updated={} unchanged={}",
                    result.created(), result.linked(), result.updated(), result.unchanged());
        } catch (RuntimeException e) {
            // Never block startup over the catalog; the next start retries (seeding is idempotent).
            log.error("event=ingredient_catalog_seed_failed errorType={}", e.getClass().getSimpleName(), e);
        }
    }

    public SeedResult seed() {
        JsonNode root = RecipeStepVocabularyProvider.readCatalogResource();
        Map<String, String> unitCategories = new HashMap<>();
        root.path("units").forEach(unit -> unitCategories.put(unit.path("id").asText(), unit.path("category").asText()));
        SeedResult result = seed(readEntries(root), unitCategories);
        eventPublisher.publishEvent(new IngredientCatalogChangedEvent("seed"));
        return result;
    }

    SeedResult seed(List<SeedEntry> entries, Map<String, String> unitCategories) {
        List<Ingredient> existing = ingredientRepository.findAll();
        Map<String, Ingredient> bySlug = new HashMap<>();
        Map<String, Ingredient> byName = new HashMap<>();
        for (Ingredient ingredient : existing) {
            if (ingredient.getCatalogSlug() != null) bySlug.put(ingredient.getCatalogSlug(), ingredient);
            if (ingredient.getName() != null) byName.putIfAbsent(normalize(ingredient.getName()), ingredient);
        }

        int created = 0, linked = 0, updated = 0, unchanged = 0;
        for (SeedEntry entry : entries) {
            Ingredient ingredient = bySlug.get(entry.slug());
            boolean isNew = false, isLinked = false;
            if (ingredient == null) {
                ingredient = findUnlinkedByName(entry, byName);
                isLinked = ingredient != null;
            }
            if (ingredient == null) {
                ingredient = new Ingredient();
                ingredient.setName(entry.name());
                ingredient.setDefaultUnit(shopUnitFor(entry.defaultUnit(), unitCategories));
                ingredient.setCreatedAt(LocalDateTime.now());
                isNew = true;
            }

            boolean changed = fillMissing(ingredient, entry);
            if (!isNew && !changed) {
                unchanged++;
                continue;
            }
            ingredient.setUpdatedAt(LocalDateTime.now());
            if (isNew) ingredient.setId(sequenceGenerator.generateSequence(Ingredient.SEQUENCE_NAME));
            try {
                Ingredient saved = ingredientRepository.save(ingredient);
                bySlug.put(entry.slug(), saved);
                byName.putIfAbsent(normalize(saved.getName()), saved);
                if (isNew) created++;
                else if (isLinked) linked++;
                else updated++;
            } catch (DuplicateKeyException e) {
                log.warn("event=ingredient_catalog_seed_skipped slug={} reason=duplicate_key", entry.slug());
            }
        }
        return new SeedResult(created, linked, updated, unchanged);
    }

    private static Ingredient findUnlinkedByName(SeedEntry entry, Map<String, Ingredient> byName) {
        List<String> candidates = new ArrayList<>();
        candidates.add(entry.name());
        candidates.addAll(entry.aliases());
        for (String candidate : candidates) {
            Ingredient match = byName.get(normalize(candidate));
            if (match != null && match.getCatalogSlug() == null) return match;
        }
        return null;
    }

    /** Fills every catalog field that is still empty; returns whether anything changed. */
    private static boolean fillMissing(Ingredient ingredient, SeedEntry entry) {
        boolean changed = false;
        if (ingredient.getCatalogSlug() == null) { ingredient.setCatalogSlug(entry.slug()); changed = true; }
        if (isBlank(ingredient.getCategory())) { ingredient.setCategory(entry.category()); changed = true; }
        if (isBlank(ingredient.getIcon()) && !isBlank(entry.icon())) { ingredient.setIcon(entry.icon()); changed = true; }
        if (isEmpty(ingredient.getAliases()) && !entry.aliases().isEmpty()) {
            ingredient.setAliases(new ArrayList<>(entry.aliases()));
            changed = true;
        }
        if (isEmpty(ingredient.getRecipeUnits())) {
            List<String> units = new ArrayList<>();
            if (!isBlank(entry.defaultUnit())) units.add(entry.defaultUnit());
            entry.units().stream().filter(unit -> !Objects.equals(unit, entry.defaultUnit())).forEach(units::add);
            if (!units.isEmpty()) { ingredient.setRecipeUnits(units); changed = true; }
        }
        if (ingredient.getPreparationStyleSets() == null && entry.preparationStyleSets() != null) {
            ingredient.setPreparationStyleSets(new ArrayList<>(entry.preparationStyleSets()));
            changed = true;
        }
        return changed;
    }

    /** The shop/inventory unit an ingredient is sold and stocked in, from its recipe default unit. */
    static UnitType shopUnitFor(String recipeUnit, Map<String, String> unitCategories) {
        if (recipeUnit == null) return UnitType.GRAM;
        if ("piece".equals(recipeUnit)) return UnitType.COUNT;
        String category = unitCategories.getOrDefault(recipeUnit, "");
        return switch (category) {
            case "weight" -> "kg".equals(recipeUnit) ? UnitType.KG : UnitType.GRAM;
            case "volume", "spoon" -> "l".equals(recipeUnit) ? UnitType.LITER : UnitType.ML;
            default -> UnitType.GRAM;
        };
    }

    static List<SeedEntry> readEntries(JsonNode root) {
        List<SeedEntry> entries = new ArrayList<>();
        root.path("ingredients").forEach(node -> {
            String slug = node.path("id").asText();
            if (slug.isBlank() || RecipeStepVocabularyProvider.CUSTOM_ID.equals(slug)) return;
            entries.add(new SeedEntry(
                    slug,
                    node.path("name").asText(),
                    node.path("category").asText(null),
                    node.path("icon").asText(null),
                    node.path("defaultUnit").asText(null),
                    strings(node.path("units")),
                    strings(node.path("aliases")),
                    node.has("preparationStyleSets") ? strings(node.path("preparationStyleSets")) : null));
        });
        return entries;
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return values;
    }

    static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
