package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Represents a single node (step) in a generated recipe execution flow graph, with
 * a node type and an open-ended data payload describing that step for rendering.
 * Used together with {@link RecipeExecutionEdgeDTO} in AI-driven recipe flow
 * generation for the process-flow visualization UI.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeExecutionStepDTO {

    private String id;
    private String nodeType;
    private Map<String, Object> data;
}
