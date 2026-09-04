package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "process_execution")
public class RecipeProcessExecution {

    public static final String SEQUENCE_NAME = "process_execution_sequence";

    @Id
    private Long id;

    @Indexed
    private Long processTemplateId;

    @Indexed
    private Long userId;

    private Long kitchenId;

    private RecipeProcessStatus status;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private String generatedMediaUrl;
}
