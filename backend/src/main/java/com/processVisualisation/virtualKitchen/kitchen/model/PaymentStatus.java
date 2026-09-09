package com.processVisualisation.virtualKitchen.kitchen.model;

/**
 * Represents the payment lifecycle state of an {@code Order}: whether payment is
 * still outstanding, has been successfully collected, or has failed.
 */
public enum PaymentStatus {
    PENDING,
    PAID,
    FAILED
}
