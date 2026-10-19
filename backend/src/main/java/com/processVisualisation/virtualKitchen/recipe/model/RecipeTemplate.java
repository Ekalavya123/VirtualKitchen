package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MongoDB document representing a reusable recipe definition ("process template"),
 * with a {@link Visibility} controlling who can see it. {@code SEQUENCE_NAME}
 * names the counter used to generate its id.
 *
 * <p>This is the aggregate root of the Recipe -&gt; Process -&gt;
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

    /**
     * The recipe's own default preparation guidance (routine washing, peeling, patting dry...) shown in the
     * editor's Preparation section, so generated and written steps needn't repeat it. Null means the
     * application's default text.
     */
    private String preparationNotes;

    /** The id of this recipe's top-level (type MAIN) {@link Process}. Null until migrated/created. */
    private Long mainProcessId;

    /**
     * The public recipe this one was copied from via "Add to My Recipes", so the app can tell a reader they
     * already have it. Null for recipes created from scratch (and for copies made before this was recorded).
     */
    private Long copiedFromRecipeId;

    /**
     * Optimistic-concurrency counter for the recipe-level process save ({@code PUT .../processes}):
     * bumped atomically by {@code RecipeTemplateRepository.claimProcessRevision} on every batch save,
     * so a client saving from a stale base is rejected instead of silently overwriting a newer save.
     * Null (absent on older documents) means 0. Read-only for whole-document saves (ingredients,
     * nutrition, copy...), which must never write back a stale value read before a concurrent claim.
     */
    @ReadOnlyProperty
    private Long processRevision;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
