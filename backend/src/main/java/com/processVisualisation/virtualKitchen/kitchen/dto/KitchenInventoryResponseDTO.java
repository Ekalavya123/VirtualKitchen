package com.processVisualisation.virtualKitchen.kitchen.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

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
