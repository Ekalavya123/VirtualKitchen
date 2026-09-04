package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class RecipeExecutionStepDTO {
    private String id;
    private String nodeType;
    private Map<String, Object> data = new LinkedHashMap<>();
}

