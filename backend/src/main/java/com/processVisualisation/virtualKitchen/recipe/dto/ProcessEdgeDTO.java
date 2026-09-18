package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Representation of a {@code Process.ProcessEdge} for the Process API: the
 * graph connection (which node leads to which, and under what label — e.g. a
 * condition's YES/NO branch) plus the presentation fields already defined on
 * the entity (handles/style/animation), carried through unchanged.
 *
 * <p>{@code @NoArgsConstructor}/{@code @AllArgsConstructor} are required
 * alongside {@code @Builder} so Jackson can deserialize this class from the
 * incoming update request body — see {@link ProcessNodeDTO} for why.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessEdgeDTO {
    private String id;
    private String source;
    private String target;
    private String label;

    // --- presentation-only fields, passed through unchanged ---
    private String sourceHandle;
    private String targetHandle;
    private String type;
    private Boolean animated;
    private Map<String, Object> style;
    private Map<String, Object> data;
}
