package com.processVisualisation.virtualKitchen.store.units;

/**
 * A quantity that cannot be converted safely (unknown unit, missing quantity, no ingredient to
 * convert for). Callers report it — it is never treated as a zero quantity.
 */
public class UnitConversionException extends RuntimeException {

    public UnitConversionException(String message) {
        super(message);
    }
}
