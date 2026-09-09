package com.processVisualisation.virtualKitchen.ai.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response representation of a single generated visualization clip (image or
 * video segment) belonging to a recipe flow, including its ordering, media
 * URLs, generation status, and linkage to its parent clip/source node.
 */
@Data
@Builder
public class VisualizationClipResponseDTO {
    private Long id;
    private String clipId;
    private String parentClipId;
    private String title;
    private String description;
    private int stepOrder;
    private String mediaUrl;
    private String thumbnailUrl;
    private String status;
    private String cameraAngle;
    private String sourceNodeId;
}
