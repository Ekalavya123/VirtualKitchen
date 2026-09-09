package com.processVisualisation.virtualKitchen.store.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Response payload representing an equipment catalog entry, including its
 * identifier and creation/update timestamps.
 */
@Data
@Builder
public class EquipmentResponseDTO {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
