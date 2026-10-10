package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus;

import java.util.EnumSet;
import java.util.Set;

import static com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus.*;

/**
 * The recipe order state machine: which actions each status allows, and where the simulated
 * preparation goes next. Pure and side-effect free, so the rules are tested on their own and the
 * service never lets a client choose a status.
 */
public final class RecipeOrderLifecycle {

    /** Details (servings, address, notes) may still change; doing so returns the order to DRAFT. */
    static final Set<RecipeOrderStatus> EDITABLE = EnumSet.of(DRAFT, AWAITING_INGREDIENTS, INVENTORY_CONFLICT, PAYMENT_FAILED);

    /** The recipe may be (re)confirmed and snapshotted. */
    static final Set<RecipeOrderStatus> CONFIRMABLE = EnumSet.of(DRAFT);

    /** Ingredients may be reserved for payment from here. */
    static final Set<RecipeOrderStatus> RESERVABLE = EnumSet.of(AWAITING_INGREDIENTS, INVENTORY_CONFLICT, PAYMENT_FAILED);

    /** Cancelling is allowed until preparation starts. */
    static final Set<RecipeOrderStatus> CANCELLABLE = EnumSet.of(
            DRAFT, AWAITING_INGREDIENTS, INVENTORY_CONFLICT, PAYMENT_FAILED, AWAITING_PAYMENT, CONFIRMED);

    /** The simulation can move the order on from here. */
    static final Set<RecipeOrderStatus> ADVANCEABLE = EnumSet.of(CONFIRMED, PREPARING, QUALITY_CHECK, OUT_FOR_DELIVERY, COMPLETING);

    private RecipeOrderLifecycle() {
    }

    /** Where the simulation goes next from {@code status} at step {@code stepIndex} of {@code totalSteps}. */
    public record Next(RecipeOrderStatus status, Integer stepIndex) {}

    /**
     * The next simulated stage. Only paid statuses advance, so an unpaid order can never reach
     * completion. OUT_FOR_DELIVERY leads to COMPLETING, where ingredient consumption is finalised
     * before the order becomes COMPLETED.
     *
     * @throws RecipeOrderException (409) when {@code status} doesn't advance
     */
    public static Next next(RecipeOrderStatus status, Integer stepIndex, int totalSteps) {
        return switch (status) {
            case CONFIRMED -> totalSteps > 0 ? new Next(PREPARING, 0) : new Next(QUALITY_CHECK, null);
            case PREPARING -> {
                int current = stepIndex == null ? 0 : stepIndex;
                yield current + 1 < totalSteps ? new Next(PREPARING, current + 1) : new Next(QUALITY_CHECK, null);
            }
            case QUALITY_CHECK -> new Next(OUT_FOR_DELIVERY, null);
            case OUT_FOR_DELIVERY, COMPLETING -> new Next(COMPLETED, null);
            default -> throw RecipeOrderException.conflict("An order that is " + label(status) + " cannot move on to preparation or delivery");
        };
    }

    static void require(Set<RecipeOrderStatus> allowed, RecipeOrderStatus status, String action) {
        if (!allowed.contains(status)) {
            throw RecipeOrderException.conflict("Cannot " + action + " an order that is " + label(status));
        }
    }

    static String label(RecipeOrderStatus status) {
        return status.name().toLowerCase().replace('_', ' ');
    }
}
