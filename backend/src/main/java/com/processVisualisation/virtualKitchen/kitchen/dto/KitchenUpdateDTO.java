package com.processVisualisation.virtualKitchen.kitchen.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request payload for updating an existing kitchen's mutable fields. Currently
 * carries only the new {@code name}, which must not be blank.
 */
@Data
public class KitchenUpdateDTO {

    @NotBlank
    private String name;
}
