package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Result of an AI EDIT request: the smallest validated set of operations that carries out the instruction on
 * {@code targetProcessId}, or a {@code clarification} question (with no operations) when the instruction was
 * ambiguous or asked for a whole new recipe. Like generation results, nothing is persisted — the frontend applies
 * the operations to the working session after the user reviews them.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeProcessEditResultDTO {
    private Long targetProcessId;
    private String summary;
    private String clarification;
    private List<ProcessEditOperationDTO> operations;
}
