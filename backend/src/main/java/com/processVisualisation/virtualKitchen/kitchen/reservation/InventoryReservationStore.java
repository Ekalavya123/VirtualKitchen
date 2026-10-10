package com.processVisualisation.virtualKitchen.kitchen.reservation;

import com.processVisualisation.virtualKitchen.store.model.Inventory;

import java.util.Collection;
import java.util.List;

/**
 * Reads kitchen ingredient stock and holds, releases and consumes parts of it for recipe orders.
 * <p>
 * Every mutation is one atomic single-document update of an {@link Inventory} row that also records
 * the operation's key in {@link Inventory#getAppliedOps()}, and is only applied when that key is not
 * there yet — so concurrent orders can never hold more than {@code quantity - reservedQuantity}, and
 * retrying any operation (after a timeout or a crash) never applies it twice. No multi-document
 * transaction is needed.
 */
public interface InventoryReservationStore {

    /** Allowed rounding slack when comparing double quantities. */
    double EPSILON = 1e-9;

    enum Outcome {
        /** The operation changed the row now. */
        APPLIED,
        /** The operation's key was already on the row — an earlier attempt applied it. */
        ALREADY_APPLIED,
        /** Not enough free stock on the row (reserve only). */
        INSUFFICIENT,
        /** The reservation was never applied, so there is nothing to release or consume. */
        NOT_RESERVED,
        /** The reservation was already released (consume) or consumed (release). */
        CLOSED,
        /** No such row. */
        ROW_MISSING
    }

    /**
     * Ingredient rows usable by {@code kitchenId}: the kitchen's own rows plus the owner's legacy
     * user-scoped rows that have no kitchen yet (the same fallback the shop uses when adding stock).
     */
    List<Inventory> findIngredientRows(Long kitchenId, Long userId, Collection<Long> ingredientIds);

    /** Holds {@code amount} (in the row's unit) if that much is free. */
    Outcome reserve(Long inventoryId, double amount, String reserveKey);

    /** Gives back a held {@code amount}; a no-op ({@link Outcome#NOT_RESERVED}) if it was never held. */
    Outcome release(Long inventoryId, double amount, String reserveKey, String releaseKey, String consumeKey);

    /** Turns a held {@code amount} into usage: on-hand and held quantity both go down by it. */
    Outcome consume(Long inventoryId, double amount, String reserveKey, String releaseKey, String consumeKey);
}
