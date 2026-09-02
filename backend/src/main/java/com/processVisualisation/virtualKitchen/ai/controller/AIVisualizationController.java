package com.processVisualisation.virtualKitchen.ai.controller;

import com.processVisualisation.virtualKitchen.ai.dto.VisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.ai.service.AIVisualizationService;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/visualizations")
public class AIVisualizationController {

    private final AIVisualizationService AIVisualizationService;

    public AIVisualizationController(AIVisualizationService AIVisualizationService) {
        this.AIVisualizationService = AIVisualizationService;
    }

    @PostMapping("/{flowId}")
    public ApiResponse<VisualizationResponseDTO> generate(@PathVariable String flowId) {
        return build(AIVisualizationService.generateVisualization(flowId), "generated");
    }

    private <T> ApiResponse<T> build(T data, String msg) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(msg)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
