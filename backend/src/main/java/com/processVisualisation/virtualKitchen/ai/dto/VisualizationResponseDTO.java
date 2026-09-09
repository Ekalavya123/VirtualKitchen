package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response payload summarizing the outcome of a visualization generation
 * request, including the list of generated clips and the final assembled
 * clip for the process template.
 */
@Data
@Builder
public class VisualizationResponseDTO {
    private Long processTemplateId;
    private String message;
    private List<VisualizationClipResponseDTO> clips;
    private VisualizationClipResponseDTO finalClip;
}
