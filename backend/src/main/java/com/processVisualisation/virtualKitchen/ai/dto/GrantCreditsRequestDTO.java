package com.processVisualisation.virtualKitchen.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Admin request to manually adjust a user's available AI credit balance. */
@Data
public class GrantCreditsRequestDTO {

    /** Signed amount to add (positive) or deduct (negative) from the user's available balance. */
    @NotNull(message = "amount is required")
    private Integer amount;
}
