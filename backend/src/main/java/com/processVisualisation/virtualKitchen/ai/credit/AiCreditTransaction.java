package com.processVisualisation.virtualKitchen.ai.credit;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsageSummary;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Append-only audit ledger entry recorded alongside every {@link
 * UserAiCredit} balance mutation. The balance document is always the fast-path
 * source of truth for "can this user spend right now"; if a ledger write ever
 * fails after its paired balance write succeeded, only this audit trail is
 * incomplete, not the balance itself (see {@link CreditService}).
 */
@Data
@Document(collection = "ai_credit_transactions")
public class AiCreditTransaction {

    @Id
    private String id;

    @Indexed
    private Long userId;

    /** Correlates this entry back to the {@code AiRequestJob} that caused it, if any. */
    @Indexed
    private String requestId;

    private CreditTransactionType type;

    /** Signed: negative for RESERVE/CONSUME, positive for RELEASE/GRANT. */
    private int amount;

    private int balanceAfter;
    private AiCapability capability;
    private String modelKey;
    private ModelTier tier;

    /**
     * Provider token usage of the request this CONSUME/RELEASE settles (a released request still spent tokens with
     * the provider). Null for entries not tied to a provider call (RESERVE, grants, adjustments).
     */
    private AiUsageSummary usage;

    /** Estimated provider cost in USD of that request (same value as {@code usage.estimatedCostUsd}), for direct querying. */
    private Double estimatedCostUsd;

    @CreatedDate
    private LocalDateTime createdAt;
}
