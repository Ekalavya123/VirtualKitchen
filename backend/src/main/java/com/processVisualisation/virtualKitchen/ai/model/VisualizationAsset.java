package com.processVisualisation.virtualKitchen.ai.model;

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

    private String videoPrompt;

    private String videoUrl;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
