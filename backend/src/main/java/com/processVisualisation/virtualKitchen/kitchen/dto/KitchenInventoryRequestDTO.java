package com.processVisualisation.virtualKitchen.kitchen.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request payload for associating an inventory item with a kitchen (allocating
 * an existing inventory record to kitchen-level scope). Both {@code kitchenId}
 * and {@code inventoryId} are required.
 */
@Data
public class KitchenInventoryRequestDTO {

    @NotNull
    private Long kitchenId;

    @NotNull
    private Long inventoryId;
}
