package com.processVisualisation.virtualKitchen.store.units;

import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService.Basis;
import com.processVisualisation.virtualKitchen.store.units.UnitConversionService.ConversionResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bundled unit-conversions.json defaults, plus per-ingredient overrides. */
class UnitConversionServiceTest {

    private final UnitConversionService conversions = UnitConversionService.withDefaults();

    @Test
    void comparesCompatibleMassUnitsExactly() {
        ConversionResult result = conversions.convert(500.0, "g", UnitType.KG, null);
        assertEquals(0.5, result.value(), 1e-9);
        assertEquals(Basis.EXACT, result.basis());

        assertEquals(500, conversions.convert(0.5, "KG", UnitType.GRAM, null).value(), 1e-9);
    }

    @Test
    void comparesCompatibleVolumeUnitsExactly() {
        assertEquals(1000, conversions.convert(1.0, "l", UnitType.ML, null).value(), 1e-9);
        assertEquals(1.0, conversions.convert(1000.0, "ML", UnitType.LITER, null).value(), 1e-9);
        assertEquals(45, conversions.convert(3.0, "tbsp", UnitType.ML, null).value(), 1e-9);
        assertEquals(Basis.EXACT, conversions.convert(3.0, "tbsp", UnitType.ML, null).basis());
    }

    @Test
    void descriptiveUnitsUseConfiguredDefaultFactorsAndSayso() {
        ConversionResult cloves = conversions.convert(2.0, "clove", UnitType.GRAM, null);
        assertEquals(10, cloves.value(), 1e-9);
        assertEquals(Basis.DEFAULT_FACTOR, cloves.basis());
        assertTrue(cloves.note().contains("clove"));
    }

    @Test
    void ingredientOverridesWinOverDefaults() {
        Ingredient garlic = new Ingredient();
        garlic.setUnitWeightsG(Map.of("clove", 4.0));
        ConversionResult cloves = conversions.convert(3.0, "clove", UnitType.GRAM, garlic);
        assertEquals(12, cloves.value(), 1e-9);
        assertEquals(Basis.INGREDIENT_OVERRIDE, cloves.basis());

        Ingredient flour = new Ingredient();
        flour.setDensityGPerMl(0.5);
        assertEquals(120, conversions.convert(1.0, "cup", UnitType.GRAM, flour).value(), 1e-9);
    }

    @Test
    void crossingDimensionsUsesDensityOrPieceWeight() {
        // 1 cup = 240 ml at the default 1 g/ml.
        ConversionResult cupInGrams = conversions.convert(1.0, "cup", UnitType.GRAM, null);
        assertEquals(240, cupInGrams.value(), 1e-9);
        assertEquals(Basis.DEFAULT_FACTOR, cupInGrams.basis());

        Ingredient egg = new Ingredient();
        egg.setUnitWeightsG(Map.of("piece", 50.0));
        assertEquals(2, conversions.convert(100.0, "g", UnitType.COUNT, egg).value(), 1e-9);
        assertEquals(150, conversions.convert(3.0, "piece", UnitType.GRAM, egg).value(), 1e-9);
    }

    @Test
    void nominalUnitsCountAsOneUnitOfTheirFactor() {
        ConversionResult salt = conversions.convert(null, "to-taste", UnitType.GRAM, null);
        assertEquals(2, salt.value(), 1e-9);
        assertEquals(Basis.DEFAULT_FACTOR, salt.basis());
    }

    @Test
    void unresolvableQuantitiesFailInsteadOfBecomingZero() {
        assertThrows(UnitConversionException.class, () -> conversions.convert(1.0, "custom", UnitType.GRAM, null));
        assertThrows(UnitConversionException.class, () -> conversions.convert(1.0, "furlong", UnitType.GRAM, null));
        assertThrows(UnitConversionException.class, () -> conversions.convert(1.0, null, UnitType.GRAM, null));
        assertThrows(UnitConversionException.class, () -> conversions.convert(null, "g", UnitType.GRAM, null));
        assertThrows(UnitConversionException.class, () -> conversions.convert(-1.0, "g", UnitType.GRAM, null));
    }
}
