package com.processVisualisation.virtualKitchen.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.store.catalog.IngredientCatalogChangedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Loads the recipe step-property vocabulary (actions and their per-action field/target/preparation
 * rules, units, preparation styles, heat levels, temperature and duration units) from
 * {@code stepCatalogs.data.json} — the same file the frontend's recipe catalogs are built from
 * (copied onto the classpath at build time, see backend/pom.xml) — so the recipe AI prompt
 * vocabulary and validator can never drift out of sync with the frontend's actual catalog.
 * Ingredients are the exception: they come from an {@link IngredientVocabularySource} — the
 * database ingredient catalog in the application, whose ids recipe steps store.
 * See docs/recipe-vocabulary-v2.md for the schema.
 */
@Component
public class RecipeStepVocabularyProvider {

    private static final String CATALOG_RESOURCE = "stepCatalogs.data.json";

    public static final String CUSTOM_ID = "custom";

    /** Field keys used in an action's {@code fields} map (see the catalog's {@code fieldDefinitions}). */
    public static final String FIELD_QUANTITY = "quantity";
    public static final String FIELD_PREPARATION_STYLE = "preparationStyleId";
    public static final String FIELD_TEMPERATURE = "temperature";
    public static final String FIELD_FLAME_LEVEL = "flameLevelId";
    public static final String FIELD_DURATION = "duration";
    public static final String FIELD_REPEAT_INTERVAL = "repeatInterval";

    /** What an action may act on (a STEP's Action On ingredients and/or subprocess outputs). */
    public enum ActionTarget {
        INGREDIENT, PROCESS, BOTH, NONE;

        public boolean allowsIngredients() {
            return this == INGREDIENT || this == BOTH;
        }

        public boolean allowsProcesses() {
            return this == PROCESS || this == BOTH;
        }
    }

    public enum FieldRequirement { REQUIRED, RECOMMENDED, OPTIONAL }

    public record CategoryDefinition(String id, String label) {}

    public record ActionDefinition(
            String id,
            String displayName,
            String category,
            String description,
            List<String> aliases,
            ActionTarget actionOn,
            boolean actionOnRequired,
            boolean multipleIngredients,
            Map<String, FieldRequirement> fields,
            List<String> preparationStyleSets,
            String temperatureContext,
            String visualization
    ) {
        public boolean hasField(String key) {
            return fields.containsKey(key);
        }

        public FieldRequirement fieldRequirement(String key) {
            return fields.get(key);
        }
    }

    public record IngredientDefinition(
            String id, String name, String category, String defaultUnit, List<String> units, List<String> aliases,
            List<String> preparationStyleSets
    ) {}

    public record UnitDefinition(String id, String label, String shortLabel, String category, boolean quantifiable, List<String> aliases) {}

    public record PreparationStyleSet(String id, String label, List<String> styles) {}

    private final List<CategoryDefinition> actionCategories;
    private final List<ActionDefinition> actions;
    private final List<CategoryDefinition> ingredientCategories;
    private final Map<String, List<String>> ingredientCategoryStyleSets;
    private final List<CategoryDefinition> unitCategories;
    private final List<UnitDefinition> units;
    private final List<PreparationStyleSet> preparationStyleSets;
    private final Map<String, String> preparationStyleLabels;
    private final Map<String, String> flameLevelLabels;
    private final List<String> temperatureUnitIds;
    private final Map<String, String> temperatureContextLabels;
    private final List<String> durationUnitIds;

    private final Map<String, ActionDefinition> actionById;
    private final Map<String, UnitDefinition> unitById;
    private final Map<String, PreparationStyleSet> styleSetById;

    private final Map<String, String> actionAliases;
    private final Map<String, String> unitAliases;
    private final Map<String, String> preparationStyleAliases;
    private final Map<String, String> durationUnitAliases;

    /**
     * Ingredients come from {@link #ingredientSource} (the database catalog in the application) and,
     * unlike the rest of the vocabulary, can change at runtime: they are loaded on first use and
     * reloaded by {@link #refreshIngredients()}.
     */
    private final IngredientVocabularySource ingredientSource;
    private volatile IngredientIndex ingredientIndex;
    private final AtomicLong ingredientCatalogVersion = new AtomicLong();

    private record IngredientIndex(
            List<IngredientDefinition> ingredients,
            Map<String, IngredientDefinition> byId,
            Map<String, String> aliases
    ) {}

    /** Reads every ingredient from the seed list in stepCatalogs.data.json (ids are the catalog slugs). */
    public RecipeStepVocabularyProvider() {
        this(null);
    }

    @Autowired
    public RecipeStepVocabularyProvider(IngredientVocabularySource ingredientSource) {
        JsonNode root = loadCatalog();
        this.ingredientSource = ingredientSource != null ? ingredientSource : () -> seedIngredients(root);

        this.actionCategories = categories(root, "actionCategories");
        this.actions = map(root, "actions", this::toAction);
        this.ingredientCategories = categories(root, "ingredientCategories");
        this.ingredientCategoryStyleSets = new HashMap<>();
        root.path("ingredientCategories").forEach(entry ->
                ingredientCategoryStyleSets.put(entry.path("id").asText(), strings(entry.path("preparationStyleSets"))));
        this.unitCategories = categories(root, "unitCategories");
        this.units = map(root, "units", entry -> new UnitDefinition(
                entry.path("id").asText(),
                entry.path("label").asText(),
                entry.path("shortLabel").asText(),
                entry.path("category").asText(),
                entry.path("quantifiable").asBoolean(true),
                strings(entry.path("aliases"))
        ));
        this.preparationStyleSets = map(root, "preparationStyleSets", entry -> new PreparationStyleSet(
                entry.path("id").asText(), entry.path("label").asText(), strings(entry.path("styles"))));
        this.preparationStyleLabels = labels(root, "preparationStyles", "label");
        this.flameLevelLabels = labels(root, "flameLevels", "label");
        this.temperatureUnitIds = map(root, "temperatureUnits", entry -> entry.path("id").asText());
        this.temperatureContextLabels = labels(root, "temperatureContexts", "label");
        this.durationUnitIds = map(root, "durationUnits", entry -> entry.path("id").asText());

        this.actionById = index(actions, ActionDefinition::id);
        this.unitById = index(units, UnitDefinition::id);
        this.styleSetById = index(preparationStyleSets, PreparationStyleSet::id);

        this.actionAliases = aliasLookup(root, "actions", "displayName");
        this.unitAliases = aliasLookup(root, "units", "label", "shortLabel");
        this.preparationStyleAliases = aliasLookup(root, "preparationStyles", "label");
        this.durationUnitAliases = aliasLookup(root, "durationUnits", "label");
    }

    private JsonNode loadCatalog() {
        return readCatalogResource();
    }

    /** The ingredient seed list in stepCatalogs.data.json, keyed by catalog slug. */
    public static List<IngredientDefinition> seedIngredients(JsonNode root) {
        return map(root, "ingredients", entry -> new IngredientDefinition(
                entry.path("id").asText(),
                entry.path("name").asText(),
                entry.path("category").asText(),
                entry.path("defaultUnit").asText(),
                strings(entry.path("units")),
                strings(entry.path("aliases")),
                entry.has("preparationStyleSets") ? strings(entry.path("preparationStyleSets")) : null
        ));
    }

    /** The parsed stepCatalogs.data.json, for the ingredient catalog seeder. */
    public static JsonNode readCatalogResource() {
        try (InputStream stream = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return new ObjectMapper().readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CATALOG_RESOURCE + " from the classpath", e);
        }
    }

    private IngredientIndex ingredientIndex() {
        IngredientIndex index = ingredientIndex;
        if (index == null) {
            synchronized (this) {
                index = ingredientIndex;
                if (index == null) {
                    index = buildIngredientIndex(ingredientSource.loadIngredients());
                    ingredientIndex = index;
                }
            }
        }
        return index;
    }

    private static IngredientIndex buildIngredientIndex(List<IngredientDefinition> ingredients) {
        Map<String, String> aliases = new HashMap<>();
        for (IngredientDefinition ingredient : ingredients) {
            aliases.put(normalize(ingredient.id()), ingredient.id());
            aliases.put(normalize(ingredient.name()), ingredient.id());
        }
        for (IngredientDefinition ingredient : ingredients) {
            ingredient.aliases().forEach(alias -> aliases.putIfAbsent(normalize(alias), ingredient.id()));
        }
        return new IngredientIndex(List.copyOf(ingredients), index(ingredients, IngredientDefinition::id), aliases);
    }

    /** Drops the cached ingredient list so the next lookup reloads it from the source. */
    public void refreshIngredients() {
        synchronized (this) {
            ingredientIndex = null;
            ingredientCatalogVersion.incrementAndGet();
        }
    }

    @EventListener
    public void onIngredientCatalogChanged(IngredientCatalogChangedEvent event) {
        refreshIngredients();
    }

    /** Increments whenever the ingredient list is reloaded — lets prompt builders rebuild cached prompts. */
    public long ingredientCatalogVersion() {
        return ingredientCatalogVersion.get();
    }

    private ActionDefinition toAction(JsonNode entry) {
        Map<String, FieldRequirement> fields = new LinkedHashMap<>();
        entry.path("fields").fields().forEachRemaining(field ->
                fields.put(field.getKey(), FieldRequirement.valueOf(field.getValue().asText().toUpperCase(Locale.ROOT))));
        return new ActionDefinition(
                entry.path("id").asText(),
                entry.path("displayName").asText(),
                entry.path("category").asText(),
                entry.path("description").asText(),
                strings(entry.path("aliases")),
                ActionTarget.valueOf(entry.path("actionOn").asText("BOTH")),
                entry.path("actionOnRequired").asBoolean(false),
                entry.path("multipleIngredients").asBoolean(true),
                Collections.unmodifiableMap(fields),
                strings(entry.path("preparationStyleSets")),
                entry.hasNonNull("temperatureContext") ? entry.path("temperatureContext").asText() : null,
                entry.path("visualization").asText("")
        );
    }

    private static <T> List<T> map(JsonNode root, String arrayField, Function<JsonNode, T> mapper) {
        return StreamSupport.stream(root.path(arrayField).spliterator(), false).map(mapper).toList();
    }

    private static List<CategoryDefinition> categories(JsonNode root, String arrayField) {
        return map(root, arrayField, entry -> new CategoryDefinition(entry.path("id").asText(), entry.path("label").asText()));
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(value -> values.add(value.asText()));
        return Collections.unmodifiableList(values);
    }

    private static Map<String, String> labels(JsonNode root, String arrayField, String labelField) {
        Map<String, String> labels = new LinkedHashMap<>();
        root.path(arrayField).forEach(entry -> labels.put(entry.path("id").asText(), entry.path(labelField).asText()));
        return labels;
    }

    private static <T> Map<String, T> index(List<T> entries, Function<T, String> idOf) {
        Map<String, T> byId = new LinkedHashMap<>();
        entries.forEach(entry -> byId.put(idOf.apply(entry), entry));
        return byId;
    }

    /** Maps each entry's id, label field(s) and aliases (normalized) to the entry id — for input interpretation only. */
    private static Map<String, String> aliasLookup(JsonNode root, String arrayField, String... labelFields) {
        Map<String, String> lookup = new HashMap<>();
        root.path(arrayField).forEach(entry -> {
            String id = entry.path("id").asText();
            lookup.put(normalize(id), id);
            for (String labelField : labelFields) {
                if (entry.hasNonNull(labelField)) lookup.put(normalize(entry.path(labelField).asText()), id);
            }
            entry.path("aliases").forEach(alias -> lookup.putIfAbsent(normalize(alias.asText()), id));
        });
        return lookup;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static Optional<String> resolve(Map<String, String> lookup, String value) {
        return Optional.ofNullable(lookup.get(normalize(value)));
    }

    // --- catalog lists (in catalog order) ---

    public List<CategoryDefinition> actionCategories() {
        return actionCategories;
    }

    public List<ActionDefinition> actions() {
        return actions;
    }

    public List<CategoryDefinition> ingredientCategories() {
        return ingredientCategories;
    }

    public List<IngredientDefinition> ingredients() {
        return ingredientIndex().ingredients();
    }

    public List<CategoryDefinition> unitCategories() {
        return unitCategories;
    }

    public List<UnitDefinition> units() {
        return units;
    }

    public List<PreparationStyleSet> preparationStyleSets() {
        return preparationStyleSets;
    }

    public Set<String> flameLevelIdSet() {
        return flameLevelLabels.keySet();
    }

    public List<String> temperatureUnitIds() {
        return temperatureUnitIds;
    }

    public List<String> durationUnitIds() {
        return durationUnitIds;
    }

    public Map<String, String> temperatureContextLabels() {
        return temperatureContextLabels;
    }

    // --- pipe-joined id lists (kept for existing callers) ---

    public String actionIds() {
        return joinIds(actionById.keySet());
    }

    public String ingredientIds() {
        return joinIds(ingredientIndex().byId().keySet());
    }

    public String unitIds() {
        return joinIds(unitById.keySet());
    }

    public String preparationStyleIds() {
        return joinIds(preparationStyleLabels.keySet());
    }

    public String flameLevelIds() {
        return joinIds(flameLevelLabels.keySet());
    }

    private static String joinIds(Set<String> ids) {
        return String.join("|", ids);
    }

    // --- lookups ---

    public Optional<ActionDefinition> action(String id) {
        return Optional.ofNullable(id == null ? null : actionById.get(id));
    }

    public Optional<IngredientDefinition> ingredient(String id) {
        return Optional.ofNullable(id == null ? null : ingredientIndex().byId().get(id));
    }

    public Optional<UnitDefinition> unit(String id) {
        return Optional.ofNullable(id == null ? null : unitById.get(id));
    }

    public boolean isPreparationStyleId(String id) {
        return id != null && preparationStyleLabels.containsKey(id);
    }

    public boolean isFlameLevelId(String id) {
        return id != null && flameLevelLabels.containsKey(id);
    }

    /** Resolves free text (id, label or alias, case-insensitive) to a canonical id — for interpreting input, never for output. */
    public Optional<String> resolveActionId(String text) {
        return resolve(actionAliases, text);
    }

    public Optional<String> resolveIngredientId(String text) {
        return resolve(ingredientIndex().aliases(), text);
    }

    /** Also maps the Process model's legacy UnitType values (COUNT/GRAM/KG/ML/LITER) via unit aliases. */
    public Optional<String> resolveUnitId(String text) {
        return resolve(unitAliases, text);
    }

    public Optional<String> resolvePreparationStyleId(String text) {
        return resolve(preparationStyleAliases, text);
    }

    public Optional<String> resolveDurationUnit(String text) {
        return resolve(durationUnitAliases, text);
    }

    /**
     * Every preparation style the action may use, in catalog order (empty when the action takes no
     * preparation style). The catalog's "custom" style is not included — callers decide whether to accept it.
     */
    public Set<String> allowedPreparationStyles(String actionId) {
        return action(actionId)
                .map(action -> stylesOfSets(action.preparationStyleSets()))
                .orElse(Set.of());
    }

    /**
     * The styles offered for one ingredient on one step: the action's sets intersected with the
     * ingredient's own sets (its override, else its category's). A custom/unknown ingredient gets the
     * action's full set.
     */
    public Set<String> allowedPreparationStyles(String actionId, String ingredientId) {
        Set<String> forAction = allowedPreparationStyles(actionId);
        Optional<IngredientDefinition> ingredient = ingredient(ingredientId);
        if (forAction.isEmpty() || ingredient.isEmpty() || CUSTOM_ID.equals(ingredientId)) return forAction;

        List<String> ingredientSets = ingredient.get().preparationStyleSets() != null
                ? ingredient.get().preparationStyleSets()
                : ingredientCategoryStyleSets.getOrDefault(ingredient.get().category(), List.of());
        Set<String> forIngredient = stylesOfSets(ingredientSets);
        return forAction.stream().filter(forIngredient::contains).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> stylesOfSets(List<String> setIds) {
        Set<String> styles = new LinkedHashSet<>();
        for (String setId : setIds) {
            PreparationStyleSet set = styleSetById.get(setId);
            if (set != null) styles.addAll(set.styles());
        }
        return styles;
    }

    /** False for non-numeric units such as "to-taste"/"as-needed", which carry no quantity. */
    public boolean isQuantifiableUnit(String unitId) {
        return unit(unitId).map(UnitDefinition::quantifiable).orElse(true);
    }

    /** Human-readable label for an action id (e.g. {@code "cut"} -> {@code "Cut"}), for AI prompts. */
    public String actionLabel(String id) {
        return action(id).map(ActionDefinition::displayName).orElse(id);
    }

    /** Human-readable label for a catalog ingredient id (e.g. {@code "onion"} -> {@code "Onion"}). */
    public String ingredientLabel(String id) {
        return ingredient(id).map(IngredientDefinition::name).orElse(id);
    }

    /** Short display label for a unit id or a legacy UnitType value (e.g. {@code "GRAM"} -> {@code "g"}); blank for a plain piece count. */
    public String unitLabel(String idOrLegacy) {
        return resolveUnitId(idOrLegacy)
                .filter(id -> !"piece".equals(id) && !CUSTOM_ID.equals(id))
                .flatMap(this::unit)
                .map(UnitDefinition::shortLabel)
                .orElse("");
    }

    /** Human-readable label for a preparation style id (e.g. {@code "thin-slice"} -> {@code "Thinly Sliced"}). */
    public String preparationStyleLabel(String id) {
        return preparationStyleLabels.getOrDefault(id, id);
    }

    /** Human-readable label for a heat level id (e.g. {@code "medium-high"} -> {@code "Medium-High"}). */
    public String flameLevelLabel(String id) {
        return flameLevelLabels.getOrDefault(id, id);
    }
}
