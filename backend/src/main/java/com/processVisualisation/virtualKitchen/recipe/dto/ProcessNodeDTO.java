package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Representation of a {@code Process.ProcessNode} for the Process API: the
 * semantic fields ({@code id}, {@code kind}, {@code data}) plus the
 * presentation fields already defined on the entity (position/size/drag
 * -select flags), carried through unchanged so the frontend can render and
 * re-save the canvas exactly as the user left it. The backend does not
 * interpret or validate the presentation fields, or a STEP's Action On
 * content inside {@code data} — those stay a frontend/React Flow concern.
 *
 * <p>{@code @NoArgsConstructor}/{@code @AllArgsConstructor} are required
 * alongside {@code @Builder} so Jackson can actually deserialize this class
 * from the incoming update request body — {@code @Builder} alone suppresses
 * Lombok's implicit no-args constructor, leaving Jackson with no usable
 * constructor ("Cannot construct instance ... no Creators exist").</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessNodeDTO {
    private String id;
    private ProcessNodeKind kind;
    private Map<String, Object> data;

    // --- presentation-only fields, passed through unchanged ---
    private String type;
    private ProcessPositionDTO position;
    private ProcessMeasuredDTO measured;
    private Double width;
    private Double height;
    private String parentId;
    private String extent;
    private Boolean draggable;
    private Boolean selectable;
    private Boolean deletable;
}
