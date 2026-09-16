package com.processVisualisation.virtualKitchen.ai.model;

import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * MongoDB document representing a single AI-generated visualization asset
 * (an image and/or video, with the prompts used to produce them) stored in
 * the {@code visualization_asset} collection.
 */
@Data
@Document(collection = "visualization_asset")
public class VisualizationAsset {

    public static final String SEQUENCE_NAME = "visualization_asset_sequence";

    @Id
    private Long id;

    @Indexed(unique = true)
    private String visualizationKey;

    private VisualizationAssetType type;

    private String imagePrompt;

    private String imageUrl;

    /**
     * Why {@link #imageUrl} is null, when it is — the generation or upload failure's
     * real cause, rather than the generic "image generation failed" the job layer used
     * to infer from a null URL alone. Null whenever {@link #imageUrl} is populated.
     */
    private String imageFailureReason;

    private String videoPrompt;

    private String videoUrl;

    /** Registry key of the model that produced {@link #imageUrl} (the image-generation call, not the prompt-authoring one). */
    private String resolvedModelKey;

    private ModelTier resolvedTier;

    /** Whether {@link #resolvedModelKey} was a fallback because premium credits were exhausted. */
    private boolean usedFallback;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
