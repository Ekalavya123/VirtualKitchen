package com.processVisualisation.virtualKitchen.ai.globalasset.dto;

import lombok.Data;

import java.util.List;

/** Request body naming the catalog resources to generate images for. */
@Data
public class GlobalAssetBatchRequestDTO {

    private List<Long> resourceIds;
}
