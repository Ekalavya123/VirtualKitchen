package com.processVisualisation.virtualKitchen.recipe.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * One process's replacement content within a recipe-level batch save (see
 * {@link ProcessBatchUpdateRequestDTO}) — the same fields as
 * {@link ProcessUpdateDTO}, plus the {@code processId} identifying which
 * process this item applies to (implicit in the URL for the single-process
 * {@code PUT /processes/{processId}} endpoint, but explicit here since a
 * batch covers many processes in one request).
 */
@Data
public class ProcessBatchItemDTO {

    @NotNull
    private Long processId;

    @NotBlank
    private String name;

    private String description;

    private List<ProcessNodeDTO> nodes;
    private List<ProcessEdgeDTO> edges;
    private ProcessViewportDTO viewport;
}
