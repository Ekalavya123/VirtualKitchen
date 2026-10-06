package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifact;
import com.processVisualisation.virtualKitchen.ai.artifact.consumer.AiArtifactConsumer;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient.GeneratedImage;
import com.processVisualisation.virtualKitchen.restclient.client.ImageStorageClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Uploads a generated global-resource image to object storage, marks its asset READY and
 * publishes the URL onto the catalog entry (ingredient, equipment).
 * <p>
 * The global counterpart of {@code VisualizationImageArtifactConsumer}: the image bytes are
 * staged as an {@link AiArtifact} before this runs, so a storage outage never costs a second paid
 * generation, and {@code AiArtifactRecoveryJob} re-drives this same bean later. Because publishing
 * happens here rather than in the request path, a recovered upload also reaches the catalog.
 */
@Component
public class GlobalAssetImageArtifactConsumer implements AiArtifactConsumer {

    public static final String CONSUMER_ID = "global-asset-image-upload";

    private static final Logger logger = LoggerFactory.getLogger(GlobalAssetImageArtifactConsumer.class);

    private final ImageStorageClient imageStorageClient;
    private final GlobalAssetStore assetStore;
    private final GlobalResourceResolver resourceResolver;

    public GlobalAssetImageArtifactConsumer(
            ImageStorageClient imageStorageClient,
            GlobalAssetStore assetStore,
            GlobalResourceResolver resourceResolver) {
        this.imageStorageClient = imageStorageClient;
        this.assetStore = assetStore;
        this.resourceResolver = resourceResolver;
    }

    @Override
    public String consumerId() {
        return CONSUMER_ID;
    }

    /**
     * @param artifact the staged artifact; its {@code correlationId} is the asset's key
     * @param payload the decoded {@link GeneratedImage}
     * @return the public URL of the uploaded image
     */
    @Override
    public String consume(AiArtifact artifact, Object payload) {
        GeneratedImage image = (GeneratedImage) payload;
        String assetKey = artifact.getCorrelationId();

        VisualizationAsset asset = assetStore.find(assetKey)
                .orElseThrow(() -> new IllegalStateException("No global asset for key " + assetKey));
        if (asset.getResourceType() == null || asset.getResourceId() == null) {
            throw new IllegalStateException("Global asset " + assetKey + " has no resource reference");
        }

        String imagePath = String.format("global-assets/%s/%s/%s/%s.png",
                asset.getResourceType().pathSegment(), asset.getResourceId(), asset.getId(), UUID.randomUUID());
        String imageUrl = imageStorageClient.upload(image.data(), image.mimeType(), imagePath);

        assetStore.markReady(assetKey, imageUrl,
                artifact.getProducedByModelKey(), artifact.getProducedByTier(), artifact.isUsedFallback());
        resourceResolver.publishImage(asset.getResourceType(), asset.getResourceId(), imageUrl);

        logger.debug("event=global_asset_image_stored assetKey={} assetId={} artifactId={} bytes={}",
                assetKey, asset.getId(), artifact.getId(), artifact.getPayloadSize());
        return imageUrl;
    }
}
