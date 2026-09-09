package com.processVisualisation.virtualKitchen.store.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document representing a piece of kitchen equipment (e.g. pan,
 * oven, mixer) in the store catalog. Referenced by identifier from
 * {@link Inventory} and {@link ItemCost} via {@link ItemType#EQUIPMENT}.
 */
@Data
@Document(collection = "equipment")
public class Equipment {

    public static final String SEQUENCE_NAME = "equipment_sequence";

    @Id
    private Long id;

    @Indexed(unique = true)
    private String name;

    private String description;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
