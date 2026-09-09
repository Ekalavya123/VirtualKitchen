package com.processVisualisation.virtualKitchen.store.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating a new equipment catalog entry.
 */
@Data
public class EquipmentRequestDTO {
    @NotBlank
    private String name;
    private String description;
}
