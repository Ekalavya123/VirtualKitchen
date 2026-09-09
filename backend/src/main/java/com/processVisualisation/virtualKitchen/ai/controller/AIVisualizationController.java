package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.dto.VisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIVisualizationService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * REST controller for triggering AI-generated recipe flow visualizations under
 * {@code /api/v1/visualizations}. Delegates visualization generation to
 * {@link AIVisualizationService} and wraps results in the common
 * {@link ApiResponse} envelope.
 */
@RestController
@RequestMapping("/api/v1/visualizations")
public class AIVisualizationController {

    private final AIVisualizationService AIVisualizationService;

    public AIVisualizationController(AIVisualizationService AIVisualizationService) {
        this.AIVisualizationService = AIVisualizationService;
    }

    /**
     * Generates a visualization (images/video assets) for the given recipe flow.
     *
     * @param flowId identifier of the recipe flow to visualize
     * @return an {@link ApiResponse} wrapping the generated visualization details
     */
    @PostMapping("/{flowId}")
    public ApiResponse<VisualizationResponseDTO> generate(@PathVariable String flowId) {
        return build(AIVisualizationService.generateVisualization(flowId), "generated");
    }

    /**
     * Wraps a payload and message in the standard success {@link ApiResponse} envelope.
     *
     * @param data the payload to include in the response
     * @param msg the human-readable status message
     * @param <T> the payload type
     * @return the constructed {@link ApiResponse}
     */
    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
