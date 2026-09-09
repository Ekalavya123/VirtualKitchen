package com.processVisualisation.virtualKitchen.kitchen.model;

/**
 * Represents the fulfillment lifecycle state of an {@code Order}, from initial
 * placement through confirmation, cancellation, or failure.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    CANCELLED,
    FAILED
}
