package com.processVisualisation.virtualKitchen.recipeorder.model;

/**
 * A recipe order's lifecycle. The allowed moves between these are owned by
 * {@code RecipeOrderLifecycle}; clients never set a status directly.
 * <pre>
 * DRAFT -> AWAITING_INGREDIENTS -> (RESERVING) -> AWAITING_PAYMENT -> CONFIRMED
 *       -> PREPARING (one recipe step at a time) -> QUALITY_CHECK -> OUT_FOR_DELIVERY
 *       -> (COMPLETING) -> COMPLETED
 * side states: INVENTORY_CONFLICT, PAYMENT_FAILED, CANCELLED
 * </pre>
 * RESERVING and COMPLETING are short internal states held while inventory is being updated; they
 * let a crashed or retried request resume safely.
 */
public enum RecipeOrderStatus {
    /** Order details being edited; the recipe is not confirmed yet. Inventory untouched. */
    DRAFT,
    /** Recipe confirmed and snapshotted; ingredients being checked (and bought, if short). Inventory untouched. */
    AWAITING_INGREDIENTS,
    /** Inventory reservation in progress. */
    RESERVING,
    /** Ingredients reserved; waiting for the (demo) payment. */
    AWAITING_PAYMENT,
    /** The reservation failed: stock ran short (e.g. another order took it). Nothing is held. */
    INVENTORY_CONFLICT,
    /** The (demo) payment was declined; the reservation was released. */
    PAYMENT_FAILED,
    /** Paid; preparation not started yet. */
    CONFIRMED,
    /** Simulated preparation, at {@code currentStepIndex}. */
    PREPARING,
    QUALITY_CHECK,
    OUT_FOR_DELIVERY,
    /** Delivered; ingredient consumption being finalised. */
    COMPLETING,
    COMPLETED,
    CANCELLED;

    public boolean isPaid() {
        return switch (this) {
            case CONFIRMED, PREPARING, QUALITY_CHECK, OUT_FOR_DELIVERY, COMPLETING, COMPLETED -> true;
            default -> false;
        };
    }
}
