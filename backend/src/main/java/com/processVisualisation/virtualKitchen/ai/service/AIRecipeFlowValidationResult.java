package com.processVisualisation.virtualKitchen.ai.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AIRecipeFlowValidationResult {

    private boolean valid;
    private List<String> errors;
}
