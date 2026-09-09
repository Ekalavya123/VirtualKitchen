package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.Data;

/**
 * Request payload identifying the process template and/or recipe flow for
 * which AI-generated visualization assets should be produced.
 */
@Data
public class VisualizationRequestDTO {
    private Long processTemplateId;
    private String flowId;
}
