package com.processVisualisation.virtualKitchen.store.units;

import com.processVisualisation.virtualKitchen.common.web.ApiException;

import org.springframework.http.HttpStatus;

/**
 * A quantity that cannot be converted safely (unknown unit, missing quantity, no ingredient to
 * convert for). Callers report it — it is never treated as a zero quantity.
 */
public class UnitConversionException extends ApiException {

    public UnitConversionException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY, null, Outcome.CLIENT_ERROR);
    }
}
