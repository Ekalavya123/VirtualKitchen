package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/** Response payload for a user's current AI credit balance. */
@Data
@Builder
public class UserAiCreditResponseDTO {
    private Long userId;
    private int monthlyAllocation;
    private int availableBalance;
    private int reservedBalance;
    private LocalDate cycleStart;
    private LocalDate cycleEnd;
}
