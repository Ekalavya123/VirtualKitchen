package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

/**
 * Response payload confirming that a recipe's process-flow graph was saved,
 * echoing the flow/user identifiers alongside a status message.
 */
@Data
public class RecipeSaveResponseDTO {
    private String flowId;
    private String userId;
    private String message;
}
