package com.processVisualisation.virtualKitchen.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeExecutionStepDTO {

    private String id;
    private String nodeType;
    private Map<String, Object> data;
}
