package com.processVisualisation.virtualKitchen.kitchen.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Domain entity representing a single kitchen owned by a user, persisted in the
 * "kitchen" MongoDB collection.
 * <p>
 * A Kitchen is the top-level aggregate under which kitchen-level inventory
 * allocations and orders are managed. The {@code ownerId} field links the kitchen
 * to the user that owns/manages it, and {@code id} is generated from the
 * {@code SEQUENCE_NAME} sequence.
 */
@Data
@Document(collection = "kitchen")
public class Kitchen {

    public static final String SEQUENCE_NAME = "kitchen_sequence";

    @Id
    private Long id;

    private String name;

    @Indexed
    private Long ownerId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
