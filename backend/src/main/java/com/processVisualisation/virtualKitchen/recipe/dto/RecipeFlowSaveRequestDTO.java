package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Request payload for persisting a recipe's process-flow graph as edited in the
 * visualization UI, carrying the owning user/template plus the raw nodes, edges
 * and viewport state produced by the front-end flow editor.
 */
@Data
public class RecipeFlowSaveRequestDTO {
    private String flowId;
    private String userId;
    private Long templateId;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;
    private Map<String, Object> viewport;
}
