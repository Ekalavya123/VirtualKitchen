package com.processVisualisation.virtualKitchen.ai.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Document(collection = "ai_responses")
public class AIResponseDocument {

    @Id
    private String id;

    /**
     * Logical context for this response (e.g. "flow-generation").
     */
    @Indexed
    private String context;

    /**
     * The raw input/prompt that produced this response.
     */
    private String input;

    /**
     * Whether the generation was considered a success.
     */
    @Indexed
    private Boolean success;

    /**
     * The full response payload from the AI / generator including metrics (tokens, time, raw response, etc.)
     * Stored as a flexible JSON-like map.
     */
    private Map<String, Object> responseData = new LinkedHashMap<>();

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

