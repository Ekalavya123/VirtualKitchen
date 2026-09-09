package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dto.VisualizationResponseDTO;

/**
 * Service contract for generating AI-driven visualization assets (images and
 * video clips) for a recipe flow.
 */
public interface AIVisualizationService {

    /**
     * Generates the visualization clips for the given recipe flow, creating
     * any missing image/video assets via the AI provider.
     *
     * @param flowId identifier of the recipe flow (process template) to visualize
     * @return the generated visualization response containing the clips and final clip
     */
    VisualizationResponseDTO generateVisualization(String flowId);
}
