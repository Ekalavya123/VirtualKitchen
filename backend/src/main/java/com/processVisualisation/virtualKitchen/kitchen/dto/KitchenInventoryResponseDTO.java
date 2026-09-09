package com.processVisualisation.virtualKitchen.kitchen.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response payload describing a kitchen-inventory allocation, combining the
 * association ids with the underlying item's descriptive details (name, type,
 * quantity, unit) and the timestamp it was last updated.
 */
@Data
@Builder
public class KitchenInventoryResponseDTO {

    private Long id;
    private Long kitchenId;
    private Long inventoryId;
    private String itemName;
    private String itemType;
    private Double quantity;
    private String unit;
    private LocalDateTime lastUpdated;
}
