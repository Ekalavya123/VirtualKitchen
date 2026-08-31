package com.processVisualisation.virtualKitchen.recipe.dto;

import com.processVisualisation.virtualKitchen.recipe.model.ProcessStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProcessExecutionResponseDTO {

    private Long id;
    private Long processTemplateId;
    private Long userId;
    private Long kitchenId;
    private ProcessStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private String generatedMediaUrl;
}
