package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document representing a reusable recipe definition ("process template")
 * that can be executed multiple times as a {@link RecipeExecution}. Owns an
 * ordered set of steps via {@link RecipeTemplateStep} and a {@link Visibility}
 * controlling who can see it. {@code SEQUENCE_NAME} names the counter used to
 * generate its id.
 */
@Data
@Document(collection = "process_template")
public class RecipeTemplate {

    public static final String SEQUENCE_NAME = "process_template_sequence";

    @Id
    private Long id;

    private String name;
    private String description;

    @Indexed
    private Long createdBy;

    @Indexed
    private Visibility visibility = Visibility.PRIVATE;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
