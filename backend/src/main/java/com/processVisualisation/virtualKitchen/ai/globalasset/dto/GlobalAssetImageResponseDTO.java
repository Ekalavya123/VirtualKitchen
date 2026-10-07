package com.processVisualisation.virtualKitchen.ai.globalasset.dto;

import com.processVisualisation.virtualKitchen.ai.globalasset.GlobalAssetStatus;
import com.processVisualisation.virtualKitchen.ai.globalasset.GlobalResourceType;
import lombok.Builder;
import lombok.Data;

/**
 * The image state of one global resource, as returned by the admin global-asset API.
 */
@Data
@Builder
public class GlobalAssetImageResponseDTO {

    /** What a generation request did. Null on a plain status read. */
    public enum Outcome {
        /** A new generation was queued by this request. */
        QUEUED,
        /** A generation for this resource is already queued or running; no new one was started. */
        IN_PROGRESS,
        /** The resource already has an image; nothing was generated. */
        ALREADY_EXISTS,
        /** No resource with this id exists (batch requests only; single requests answer 404). */
        NOT_FOUND,
        /** The resource can't have an image generated, e.g. it has no name (batch requests only). */
        NOT_ELIGIBLE
    }

    private GlobalResourceType resourceType;
    private Long resourceId;
    private String resourceName;
    /** The global {@code VisualizationAsset} id, once one exists. */
    private Long assetId;
    /** The asset's generation status, or null when no generation was ever requested. */
    private GlobalAssetStatus status;
    private Outcome outcome;
    private String imageUrl;
    private String failureReason;
    private String message;
}
