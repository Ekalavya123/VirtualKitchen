package com.processVisualisation.virtualKitchen.common;


import lombok.Getter;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * MongoDB document model backing {@code SequenceGeneratorService}'s
 * auto-increment counters, stored in the {@code database_sequences}
 * collection. Each document represents one named counter (e.g.
 * {@code "users_sequence"}) and the last value it was incremented to.
 */
@Getter
@Document(collection = "database_sequences")
public class DBSequence {
    @Id
    private String name; // collection name (e.g., "users_sequence")
    private long seq;
}
