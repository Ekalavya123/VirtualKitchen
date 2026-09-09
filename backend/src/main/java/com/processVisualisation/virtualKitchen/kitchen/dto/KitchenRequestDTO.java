package com.processVisualisation.virtualKitchen.kitchen.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload for creating a new kitchen, carrying the kitchen's
 * {@code name} (required, non-blank) and the {@code ownerId} of the user who
 * will own it (required).
 */
@Data
public class KitchenRequestDTO {

    @NotBlank
    private String name;

    @NotNull
    private Long ownerId;
}
