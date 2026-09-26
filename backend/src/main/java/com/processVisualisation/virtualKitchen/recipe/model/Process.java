package com.processVisualisation.virtualKitchen.recipe.model;

import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MongoDB document representing one cooking process: either the MAIN process
 * of a {@link RecipeTemplate} (referenced by its {@code mainProcessId}) or a
 * SUBPROCESS belonging to the same recipe. Every process is its own
 * self-contained graph of STEP/CONDITION nodes — a process is never embedded
 * as a node inside another process's graph. A SUBPROCESS is instead
 * referenced by id from a STEP's own "Action On" data (an opaque, frontend
 * -owned field bag — see {@link ProcessNode#data}); its {@link #name} is
 * also its summary/output as far as another step's Action On is concerned —
 * there is no separate "output" field.
 *
 * <p><strong>Phase 1 scope:</strong> a process belongs to exactly one recipe
 * ({@link #recipeId}); there is no cross-recipe sharing, no global process
 * library, and no versioning yet.</p>
 */
@Data
@Document(collection = "processes")
public class Process {

    public static final String SEQUENCE_NAME = "process_sequence";

    @Id
    private Long id;

    private ProcessType type = ProcessType.MAIN;

    /** The recipe this process belongs to; see {@link RecipeTemplate#getMainProcessId()}. */
    @Indexed
    private Long recipeId;

    private String name;
    private String description;

    private List<ProcessNode> nodes = new ArrayList<>();
    private List<ProcessEdge> edges = new ArrayList<>();

    private ProcessViewport viewport;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Embedded STEP or CONDITION node in a {@link Process} graph. {@link #data}
     * is a freeform field bag mirroring the flow editor's per-node data map —
     * its exact shape (including a STEP's Action -> Action On ingredient/
     * subprocess references) is a frontend concern, not enforced here. The
     * remaining fields are React Flow presentation concerns (layout, sizing,
     * drag/select behavior), carried through unchanged from the legacy
     * {@link Recipe.NodeDocument} shape.
     */
    @Data
    public static class ProcessNode {
        private String id;
        private ProcessNodeKind kind;

        private Map<String, Object> data = new LinkedHashMap<>();

        // --- presentation-only fields, unchanged shape from Recipe.NodeDocument ---
        private String type;
        private PositionDocument position;
        private MeasuredDocument measured;
        private Double width;
        private Double height;
        private String parentId;
        private String extent;
        private Boolean draggable;
        private Boolean selectable;
        private Boolean deletable;
    }

    /** Embedded document representing a single edge (connection) between two {@link ProcessNode}s. */
    @Data
    public static class ProcessEdge {
        private String id;
        private String source;
        private String target;
        private String sourceHandle;
        private String targetHandle;
        private String type;
        private Boolean animated;
        private Map<String, Object> style;
        private Map<String, Object> data = new LinkedHashMap<>();
        private String label;
    }

    /** Embedded document holding the x/y canvas coordinates of a {@link ProcessNode}. */
    @Data
    public static class PositionDocument {
        private Double x;
        private Double y;
    }

    /** Embedded document holding the rendered width/height of a {@link ProcessNode}. */
    @Data
    public static class MeasuredDocument {
        private Double width;
        private Double height;
    }

    /** Embedded document holding this process's own canvas pan/zoom state. */
    @Data
    public static class ProcessViewport {
        private Double x;
        private Double y;
        private Double zoom;
    }
}
