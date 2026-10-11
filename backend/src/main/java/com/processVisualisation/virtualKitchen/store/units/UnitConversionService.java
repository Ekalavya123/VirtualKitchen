package com.processVisualisation.virtualKitchen.store.units;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a quantity of one ingredient between units — recipe units ({@code "tbsp"},
 * {@code "clove"}, {@code "to-taste"}, ...) and the shop/inventory {@link UnitType}s — so recipe
 * requirements can be compared with, reserved from and consumed from kitchen inventory.
 * <p>
 * Every unit has a dimension (mass, volume, count) and a factor in that dimension's base unit,
 * from {@code unit-conversions.json}. Crossing dimensions uses the ingredient's density or piece
 * weight. An ingredient's own {@code unitWeightsG} / {@code densityGPerMl} win over the file's
 * defaults. Each result says whether it is exact or rests on a default estimate, so the UI can show
 * which numbers are approximate. A conversion that cannot be made throws
 * {@link UnitConversionException}; it never quietly returns zero.
 */
@Service
public class UnitConversionService {

    public static final String DEFAULT_RESOURCE = "unit-conversions.json";

    public enum Dimension { MASS, VOLUME, COUNT }

    /** How trustworthy a converted figure is, from most to least certain. */
    public enum Basis { EXACT, INGREDIENT_OVERRIDE, DEFAULT_FACTOR }

    public record ConversionResult(double value, Basis basis, String note) {}

    record UnitDef(String id, Dimension dimension, double factor, boolean approximate, boolean nominal) {}

    private final Map<String, UnitDef> units = new HashMap<>();
    private final Map<String, String> inventoryUnits = new HashMap<>();
    private final double defaultDensity;
    private final double defaultGramsPerPiece;

    @Autowired
    public UnitConversionService(@Value("${vk.units.conversions-file:}") String conversionsFile) {
        this(load(conversionsFile));
    }

    public UnitConversionService(JsonNode config) {
        config.path("units").fields().forEachRemaining(entry -> {
            JsonNode unit = entry.getValue();
            units.put(entry.getKey(), new UnitDef(
                    entry.getKey(),
                    Dimension.valueOf(unit.path("dimension").asText()),
                    unit.path("factor").asDouble(),
                    unit.path("approximate").asBoolean(false),
                    unit.path("nominal").asBoolean(false)));
        });
        config.path("inventoryUnits").fields().forEachRemaining(entry -> inventoryUnits.put(entry.getKey(), entry.getValue().asText()));
        this.defaultDensity = config.path("defaults").path("densityGPerMl").asDouble(1.0);
        this.defaultGramsPerPiece = config.path("defaults").path("gramsPerPiece").asDouble(100);
    }

    /** The bundled defaults — for tests and tools. */
    public static UnitConversionService withDefaults() {
        return new UnitConversionService(load(""));
    }

    private static JsonNode load(String conversionsFile) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            if (conversionsFile != null && !conversionsFile.isBlank()) {
                return mapper.readTree(Files.readString(Path.of(conversionsFile)));
            }
            try (InputStream stream = new ClassPathResource(DEFAULT_RESOURCE).getInputStream()) {
                return mapper.readTree(stream);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load unit conversions from "
                    + (conversionsFile == null || conversionsFile.isBlank() ? DEFAULT_RESOURCE : conversionsFile), e);
        }
    }

    /** Whether {@code unit} (a recipe unit id or a {@link UnitType} name) is known. */
    public boolean isKnownUnit(String unit) {
        return resolve(unit) != null;
    }

    /** Whether the unit carries no number (to taste, as needed). */
    public boolean isNominalUnit(String unit) {
        UnitDef def = resolve(unit);
        return def != null && def.nominal();
    }

    public ConversionResult convert(Double quantity, String fromUnit, UnitType toUnit, Ingredient ingredient) {
        return convert(quantity, fromUnit, toUnit == null ? null : toUnit.name(), ingredient);
    }

    /**
     * Converts {@code quantity} of {@code ingredient} from {@code fromUnit} into {@code toUnit}.
     * A null quantity is only allowed for a nominal unit ("to taste" counts as one unit of it).
     *
     * @throws UnitConversionException when either unit is unknown or the quantity is missing/negative
     */
    public ConversionResult convert(Double quantity, String fromUnit, String toUnit, Ingredient ingredient) {
        UnitDef from = require(fromUnit);
        UnitDef to = require(toUnit);
        double amount;
        if (quantity == null) {
            if (!from.nominal()) throw new UnitConversionException("A quantity is required for unit \"" + fromUnit + "\"");
            amount = 1;
        } else {
            if (quantity < 0 || quantity.isNaN() || quantity.isInfinite()) {
                throw new UnitConversionException("Invalid quantity " + quantity);
            }
            amount = quantity;
        }

        Tracker tracker = new Tracker();
        Map<String, Double> weights = ingredient == null || ingredient.getUnitWeightsG() == null ? Map.of() : ingredient.getUnitWeightsG();

        // Into the source unit's base (or grams, when the ingredient defines that unit's weight).
        Dimension dimension;
        double base;
        Double fromWeight = positive(weights.get(from.id()));
        if (fromWeight != null && !isExactMassUnit(from)) {
            dimension = Dimension.MASS;
            base = amount * fromWeight;
            tracker.override("1 " + from.id() + " = " + format(fromWeight) + " g (ingredient)");
        } else {
            dimension = from.dimension();
            base = amount * from.factor();
            if (from.approximate()) {
                tracker.estimate((from.nominal() ? "\"" + from.id() + "\" counted as " : "1 " + from.id() + " ≈ ")
                        + format(from.factor()) + " " + baseLabel(from.dimension()));
            }
        }

        // Into the target unit.
        Double toWeight = positive(weights.get(to.id()));
        if (toWeight != null && !isExactMassUnit(to)) {
            double grams = changeDimension(base, dimension, Dimension.MASS, ingredient, tracker);
            tracker.override("1 " + to.id() + " = " + format(toWeight) + " g (ingredient)");
            return tracker.result(grams / toWeight);
        }
        double inTargetBase = changeDimension(base, dimension, to.dimension(), ingredient, tracker);
        if (to.approximate()) {
            tracker.estimate("1 " + to.id() + " ≈ " + format(to.factor()) + " " + baseLabel(to.dimension()));
        }
        return tracker.result(inTargetBase / to.factor());
    }

    private double changeDimension(double value, Dimension from, Dimension to, Ingredient ingredient, Tracker tracker) {
        if (from == to) return value;
        double grams = switch (from) {
            case MASS -> value;
            case VOLUME -> value * density(ingredient, tracker);
            case COUNT -> value * gramsPerPiece(ingredient, tracker);
        };
        return switch (to) {
            case MASS -> grams;
            case VOLUME -> grams / density(ingredient, tracker);
            case COUNT -> grams / gramsPerPiece(ingredient, tracker);
        };
    }

    private double density(Ingredient ingredient, Tracker tracker) {
        Double override = ingredient == null ? null : positive(ingredient.getDensityGPerMl());
        if (override != null) {
            tracker.override("density " + format(override) + " g/ml (ingredient)");
            return override;
        }
        tracker.estimate("density ≈ " + format(defaultDensity) + " g/ml (default)");
        return defaultDensity;
    }

    private double gramsPerPiece(Ingredient ingredient, Tracker tracker) {
        Double override = ingredient == null || ingredient.getUnitWeightsG() == null ? null : positive(ingredient.getUnitWeightsG().get("piece"));
        if (override != null) {
            tracker.override("1 piece = " + format(override) + " g (ingredient)");
            return override;
        }
        tracker.estimate("1 piece ≈ " + format(defaultGramsPerPiece) + " g (default)");
        return defaultGramsPerPiece;
    }

    private UnitDef require(String unit) {
        UnitDef def = resolve(unit);
        if (def == null) {
            throw new UnitConversionException(unit == null || unit.isBlank()
                    ? "A unit is required"
                    : "Unit \"" + unit + "\" has no conversion defined");
        }
        return def;
    }

    private UnitDef resolve(String unit) {
        if (unit == null || unit.isBlank()) return null;
        String key = unit.trim();
        String inventoryAlias = inventoryUnits.get(key.toUpperCase(Locale.ROOT));
        if (inventoryAlias != null) key = inventoryAlias;
        UnitDef def = units.get(key);
        return def != null ? def : units.get(key.toLowerCase(Locale.ROOT));
    }

    /** Standard mass units are exact; an ingredient weight override only re-defines descriptive units. */
    private static boolean isExactMassUnit(UnitDef unit) {
        return unit.dimension() == Dimension.MASS && !unit.approximate();
    }

    private static Double positive(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private static String baseLabel(Dimension dimension) {
        return switch (dimension) {
            case MASS -> "g";
            case VOLUME -> "ml";
            case COUNT -> "piece";
        };
    }

    static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 1000) / 1000.0);
    }

    /** Collects which factors a conversion relied on. */
    private static final class Tracker {
        private Basis basis = Basis.EXACT;
        private final StringBuilder notes = new StringBuilder();

        void override(String note) {
            if (basis == Basis.EXACT) basis = Basis.INGREDIENT_OVERRIDE;
            append(note);
        }

        void estimate(String note) {
            basis = Basis.DEFAULT_FACTOR;
            append(note);
        }

        private void append(String note) {
            if (notes.indexOf(note) >= 0) return;
            if (!notes.isEmpty()) notes.append("; ");
            notes.append(note);
        }

        ConversionResult result(double value) {
            return new ConversionResult(value, basis, notes.isEmpty() ? null : notes.toString());
        }
    }
}
