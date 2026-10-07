package com.processVisualisation.virtualKitchen.ai.workflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * "Approve &amp; Continue". {@code approvedRevision} is the recipe process revision the editor had
 * just saved when the user approved; approval is refused if the saved recipe has moved on since,
 * so downstream tasks never start from a state the user didn't see.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeAiWorkflowApproveRequestDTO {

    @NotNull(message = "approvedRevision is required")
    private Long approvedRevision;
}
