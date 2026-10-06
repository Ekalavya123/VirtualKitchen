package com.processVisualisation.virtualKitchen.ai.globalasset.dto;

import com.processVisualisation.virtualKitchen.ai.globalasset.GlobalResourceType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * The result of a bulk image-generation request: per-outcome counts plus each resource's result.
 */
@Data
@Builder
public class GlobalAssetBatchResponseDTO {

    private GlobalResourceType resourceType;
    private int requested;
    private int queued;
    private int inProgress;
    private int alreadyExists;
    private int notFound;
    private int notEligible;
    private List<GlobalAssetImageResponseDTO> items;

    public static GlobalAssetBatchResponseDTO of(GlobalResourceType type, List<GlobalAssetImageResponseDTO> items) {
        return GlobalAssetBatchResponseDTO.builder()
                .resourceType(type)
                .requested(items.size())
                .queued(count(items, GlobalAssetImageResponseDTO.Outcome.QUEUED))
                .inProgress(count(items, GlobalAssetImageResponseDTO.Outcome.IN_PROGRESS))
                .alreadyExists(count(items, GlobalAssetImageResponseDTO.Outcome.ALREADY_EXISTS))
                .notFound(count(items, GlobalAssetImageResponseDTO.Outcome.NOT_FOUND))
                .notEligible(count(items, GlobalAssetImageResponseDTO.Outcome.NOT_ELIGIBLE))
                .items(items)
                .build();
    }

    private static int count(List<GlobalAssetImageResponseDTO> items, GlobalAssetImageResponseDTO.Outcome outcome) {
        return (int) items.stream().filter(item -> item.getOutcome() == outcome).count();
    }
}
