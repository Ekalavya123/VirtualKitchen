package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response payload representing a persisted {@code Process} (a recipe's MAIN
 * process or one of its SUBPROCESS documents): enough for the frontend to
 * render a summary, open the process editor, identify each node's kind, and
 * restore the saved canvas (nodes/edges/viewport). A process's {@code name}
 * doubles as its summary — a STEP elsewhere references a subprocess by id
 * from its own Action On data.
 */
@Data
@Builder
public class ProcessResponseDTO {
    private Long id;
    private ProcessType type;
    private Long recipeId;
    private String name;
    private String description;
    private List<ProcessNodeDTO> nodes;
    private List<ProcessEdgeDTO> edges;
    private ProcessViewportDTO viewport;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
