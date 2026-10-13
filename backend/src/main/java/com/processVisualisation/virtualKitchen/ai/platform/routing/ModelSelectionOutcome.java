package com.processVisualisation.virtualKitchen.ai.platform.routing;

import com.processVisualisation.virtualKitchen.ai.platform.credit.CreditReservation;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelDefinition;

/**
 * The result of {@link ModelSelectionService#select}: which model to actually
 * invoke, whether that required falling back from the caller's preferred/default
 * model, and the credit reservation (if any) held against it.
 *
 * @param reservation the held credit reservation for a {@code PAID} model, or
 *                     {@code null} for an {@code OPEN_SOURCE} model (nothing to reserve)
 */
public record ModelSelectionOutcome(
        ModelDefinition model,
        boolean usedFallback,
        FallbackReason fallbackReason,
        CreditReservation reservation
) {
}
