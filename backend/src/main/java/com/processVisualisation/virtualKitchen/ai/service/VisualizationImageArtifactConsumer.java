package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.consumer.AiArtifactConsumer;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Uploads a generated visualization image to object storage and records the resulting URL on
 * its {@link VisualizationAsset}.
 * <p>
 * This is the dependent work that used to destroy the AI payload when it failed: the image bytes
 * existed only as a local variable across this upload, so any storage outage lost them and forced
 * a second paid generation. The bytes are now staged as an {@link AiArtifact} first, and this
 * consumer runs over that stored payload — from the request path on the first attempt, and from
 * {@code AiArtifactRecoveryJob} if that attempt fails or the process dies mid-upload. Throwing
 * from here is safe and expected: it leaves the artifact {@code PENDING} with its payload intact.
 * <p>
 * Both callers use this one bean so that recovery behaviour cannot drift from online behaviour.
 */
@Component
public class VisualizationImageArtifactConsumer implements AiArtifactConsumer {

    public static final String CONSUMER_ID = "visualization-image-upload";

    private static final Logger logger = LoggerFactory.getLogger(VisualizationImageArtifactConsumer.class);

    private final ImageStorageClient imageStorageClient;
    private final AIVisualizationAssetRepository assetRepository;
    private final MongoTemplate mongoTemplate;

    public VisualizationImageArtifactConsumer(
            ImageStorageClient imageStorageClient,
            AIVisualizationAssetRepository assetRepository,
            MongoTemplate mongoTemplate) {
        this.imageStorageClient = imageStorageClient;
        this.assetRepository = assetRepository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public String consumerId() {
        return CONSUMER_ID;
    }

    /**
     * Uploads the image and records its URL against the asset for this step.
     * <p>
     * The artifact's {@code correlationId} is the asset's {@code visualizationKey}, which carries
     * a unique index — so the row can be located from a recovery thread that has no request
     * context. The asset is written with {@code updateFirst} rather than a read-modify-save
     * because a request thread and a sweeper thread may both be writing this document.
     *
     * @param artifact the staged artifact, carrying the correlation and producing-model metadata
     * @param payload the decoded {@link GeneratedImage}
     * @return the public URL of the uploaded image
     */
    @Override
    public String consume(AiArtifact artifact, Object payload) {
        GeneratedImage image = (GeneratedImage) payload;
        String visualizationKey = artifact.getCorrelationId();

        VisualizationAsset asset = assetRepository.findByVisualizationKey(visualizationKey)
                .orElseThrow(() -> new IllegalStateException(
                        "No visualization asset for key " + visualizationKey));

        String imagePath = String.format(
                "visualizations/%s/%s/%s.png",
                visualizationKey,
                asset.getId(),
                UUID.randomUUID()
        );

        String imageUrl = imageStorageClient.upload(image.data(), image.mimeType(), imagePath);

        mongoTemplate.updateFirst(
                query(where("_id").is(asset.getId())),
                new Update()
                        .set("imageUrl", imageUrl)
                        .set("imageFailureReason", null)
                        .set("resolvedModelKey", artifact.getProducedByModelKey())
                        .set("resolvedTier", artifact.getProducedByTier())
                        .set("usedFallback", artifact.isUsedFallback())
                        .set("updatedAt", LocalDateTime.now()),
                VisualizationAsset.class);

        logger.info("Uploaded visualization image for key {} from artifact {} ({} bytes)",
                visualizationKey, artifact.getId(), artifact.getPayloadSize());
        return imageUrl;
    }
}
