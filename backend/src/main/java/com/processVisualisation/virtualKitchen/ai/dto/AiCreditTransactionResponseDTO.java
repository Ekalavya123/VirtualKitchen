package com.processVisualisation.virtualKitchen.ai.dto;

import com.processVisualisation.virtualKitchen.ai.credit.CreditTransactionType;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/** One entry in a user's AI credit ledger, for self-service usage history. */
@Data
@Builder
public class AiCreditTransactionResponseDTO {
    private String id;
    private String requestId;
    private CreditTransactionType type;
    private int amount;
    private int balanceAfter;
    private AiCapability capability;
    private String modelKey;
    private ModelTier tier;
    private LocalDateTime createdAt;
}
