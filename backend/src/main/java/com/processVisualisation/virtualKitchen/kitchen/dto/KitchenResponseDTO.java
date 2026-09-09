package com.processVisualisation.virtualKitchen.kitchen.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response payload returned to clients describing a kitchen, including its
 * identity, name, owner, and creation/last-updated timestamps.
 */
@Data
@Builder
public class KitchenResponseDTO {

    private Long id;
    private String name;
    private Long ownerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
