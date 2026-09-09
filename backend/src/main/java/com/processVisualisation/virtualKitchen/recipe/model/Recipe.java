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
 * MongoDB document representing a saved recipe process-flow graph ("flow") as
 * edited in the visualization UI, owned by a user and optionally derived from a
 * {@link RecipeTemplate}. Stores the graph's nodes, edges and viewport state
 * exactly as produced by the front-end flow editor.
 */
@Data
@Document(collection = "flows")
public class Recipe {

    @Id
    private String id;

    @Indexed(unique = true)
    private String flowId;

    @Indexed
    private String userId;

    @Indexed
    private Long templateId;

    private List<NodeDocument> nodes = new ArrayList<>();

    private List<EdgeDocument> edges = new ArrayList<>();

    private ViewportDocument viewport;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    /**
     * Embedded document representing a single node (step) in a {@link Recipe}
     * flow graph, including its layout position, size and arbitrary node data.
     */
    @Data
    public static class NodeDocument {
        private String id;
        private String type;
        private Map<String, Object> data = new LinkedHashMap<>();
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

    /**
     * Embedded document representing a single edge (connection) between two nodes
     * in a {@link Recipe} flow graph, including its styling and label.
     */
    @Data
    public static class EdgeDocument {
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

    /** Embedded document holding the x/y canvas coordinates of a {@link NodeDocument}. */
    @Data
    public static class PositionDocument {
        private Double x;
        private Double y;
    }

    /** Embedded document holding the rendered width/height of a {@link NodeDocument}. */
    @Data
    public static class MeasuredDocument {
        private Double width;
        private Double height;
    }

    /**
     * Embedded document holding the flow editor's viewport (pan/zoom) state for a
     * {@link Recipe}, so the canvas can be restored to how the user left it.
     */
    @Data
    public static class ViewportDocument {
        private Double x;
        private Double y;
        private Double zoom;
    }
}
