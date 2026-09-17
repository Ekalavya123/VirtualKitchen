package com.processVisualisation.virtualKitchen.ai.credit;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;

/**
 * A held-but-not-yet-finalized credit charge, returned by {@link
 * CreditService#reserve}. {@code cost} is echoed back here (rather than the
 * caller having to resupply it to {@link CreditService#consume}/{@link
 * CreditService#release}) so a mismatched amount can never be applied when
 * finalizing the reservation. {@code transactionId} is {@code null} for a
 * zero-cost (open-source) reservation, which never touches the ledger.
 */
public record CreditReservation(String transactionId, int cost, AiCapability capability, String modelKey) {
}
