package com.processVisualisation.virtualKitchen.ai.platform.credit;

/** The kind of change recorded against a {@link UserAiCredit} balance in the append-only ledger. */
public enum CreditTransactionType {
    RESERVE,
    CONSUME,
    RELEASE,
    MONTHLY_GRANT,
    ADMIN_ADJUSTMENT
}
