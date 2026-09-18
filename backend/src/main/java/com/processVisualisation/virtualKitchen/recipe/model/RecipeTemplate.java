package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document representing a reusable recipe definition ("process template")
 * that can be executed multiple times as a {@link RecipeExecution}. Owns an
 * ordered set of steps via {@link RecipeTemplateStep} and a {@link Visibility}
 * controlling who can see it. {@code SEQUENCE_NAME} names the counter used to
 * generate its id.
 *
 * <p>This is also the aggregate root of the newer Recipe -&gt; Process -&gt;
 * ProcessNode model: {@link #ingredients} and {@link #nutrition} are the
 * recipe's own data, and {@link #mainProcessId} points at this recipe's
 * top-level {@link Process} (type {@code MAIN}), whose nodes/edges hold the
 * actual step/condition/subprocess graph. All three are additive and
 * nullable/empty by default so existing documents remain valid without a
 * migration purely for that reason (Spring Data leaves a field's default
 * value untouched for any key absent from the stored document).</p>
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

    private List<RecipeIngredient> ingredients = new ArrayList<>();

    private NutritionInfo nutrition;

    /** The id of this recipe's top-level (type MAIN) {@link Process}. Null until migrated/created. */
    private Long mainProcessId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
