package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

@Data
public class RecipeExecutionEdgeDTO {
    private String from;
    private String to;
    private String label;
}

