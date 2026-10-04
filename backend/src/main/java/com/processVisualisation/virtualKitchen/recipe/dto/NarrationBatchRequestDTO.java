package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Data;

import java.util.List;

/** Optional body for ensuring narration for several steps of one process at once. */
@Data
public class NarrationBatchRequestDTO {
    /** Steps to ensure; null or empty means every step of the process. */
    private List<String> stepIds;
}
