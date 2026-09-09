package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single connection between two steps in a generated recipe execution
 * flow graph, linking a source node to a target node with an optional label.
 * Used together with {@link RecipeExecutionStepDTO} to describe the nodes/edges
 * returned by AI-driven recipe flow generation for process-flow visualization.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeExecutionEdgeDTO {

    private String from;
    private String to;
    private String label;
}
