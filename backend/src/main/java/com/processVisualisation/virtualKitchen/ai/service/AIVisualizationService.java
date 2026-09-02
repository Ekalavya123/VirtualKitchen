package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dto.VisualizationResponseDTO;

public interface AIVisualizationService {
    VisualizationResponseDTO generateVisualization(String flowId);
}
