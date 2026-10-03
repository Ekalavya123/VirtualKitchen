package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response payload for {@code PUT /api/v1/recipes/{recipeId}/processes}: the saved processes plus
 * the recipe's new process revision, which the client sends back as the next save's
 * {@code baseRevision}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessBatchUpdateResponseDTO {
    private Long revision;
    private List<ProcessResponseDTO> processes;
}
