package com.processVisualisation.virtualKitchen.recipe.controller;

import com.processVisualisation.virtualKitchen.recipe.dto.FlowSaveRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.FlowSaveResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.FlowDocument;
import com.processVisualisation.virtualKitchen.common.utils.ApiResponse;
import com.processVisualisation.virtualKitchen.recipe.service.FlowService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
@RequestMapping("/api/v1/flows")
public class FlowController {

    private final FlowService flowService;

    public FlowController(FlowService flowService) {
        this.flowService = flowService;
    }

    @PutMapping("/{flowId}")
    public ApiResponse<FlowSaveResponseDTO> saveFlow(@PathVariable String flowId,
                                                     @RequestBody FlowSaveRequestDTO request) {
        request.setFlowId(flowId);
        return build(flowService.saveFlow(request), "saved");
    }

    @GetMapping("/{flowId}")
    public ApiResponse<Optional<FlowDocument>> getFlow(@PathVariable String flowId) {
        return build(flowService.getFlow(flowId), "fetched");
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
