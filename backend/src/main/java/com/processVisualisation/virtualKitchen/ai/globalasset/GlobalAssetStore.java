package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationAsset;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationAssetType;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationAssetRepository;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Every state change of a global asset's {@link VisualizationAsset} goes through here, each as
 * one atomic Mongo operation. That is what makes the duplicate-generation guard hold across
 * concurrent admin requests and multiple application instances (the same claim/lease pattern
 * as {@code MongoStepNarrationStore}).
 */
@Component
public class GlobalAssetStore {

    private final AIVisualizationAssetRepository assetRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final MongoTemplate mongoTemplate;

    public GlobalAssetStore(
            AIVisualizationAssetRepository assetRepository,
            SequenceGeneratorService sequenceGeneratorService,
            MongoTemplate mongoTemplate) {
        this.assetRepository = assetRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<VisualizationAsset> find(String assetKey) {
        return assetRepository.findByVisualizationKey(assetKey);
    }

    /**
     * Atomically claims the right to generate {@code resource}'s image, marking it QUEUED.
     * <p>
     * Refused (empty) while the asset is READY, or while another generation's claim is QUEUED or
     * GENERATING with an unexpired lease. A FAILED asset, or one whose lease expired (its worker
     * died mid-generation), can be claimed again.
     *
     * @return the claimed asset carrying {@code token}, or empty if refused
     */
    public Optional<VisualizationAsset> claim(
            GlobalResource resource, String token, Long requestedBy, Instant now, Instant leaseUntil) {
        String assetKey = resource.type().assetKey(resource.id());
        Criteria refused = new Criteria().orOperator(
                where("generationStatus").is(GlobalAssetStatus.READY),
                where("generationStatus").in(List.of(GlobalAssetStatus.QUEUED, GlobalAssetStatus.GENERATING))
                        .and("leaseUntil").gt(now));
        Criteria claimable = where("visualizationKey").is(assetKey).norOperator(refused);

        VisualizationAsset claimed = mongoTemplate.findAndModify(
                query(claimable),
                new Update()
                        .set("generationStatus", GlobalAssetStatus.QUEUED)
                        .set("generationToken", token)
                        .set("leaseUntil", leaseUntil)
                        .set("requestedBy", requestedBy)
                        .set("updatedAt", LocalDateTime.now())
                        .unset("imageFailureReason"),
                FindAndModifyOptions.options().returnNew(true),
                VisualizationAsset.class);
        if (claimed != null) {
            return Optional.of(claimed);
        }
        if (assetRepository.findByVisualizationKey(assetKey).isPresent()) {
            return Optional.empty();
        }
        return insertFirstClaim(resource, assetKey, token, requestedBy, leaseUntil);
    }

    /** Moves a claimed asset to GENERATING with its prompt, but only while {@code token} still owns it. */
    public boolean markGenerating(String assetKey, String token, String imagePrompt) {
        return mongoTemplate.updateFirst(
                query(where("visualizationKey").is(assetKey).and("generationToken").is(token)),
                new Update()
                        .set("generationStatus", GlobalAssetStatus.GENERATING)
                        .set("imagePrompt", imagePrompt)
                        .set("updatedAt", LocalDateTime.now()),
                VisualizationAsset.class).getModifiedCount() > 0;
    }

    /**
     * Records the stored image and marks the asset READY. Not token-guarded: it is also driven by
     * the artifact recovery job, which has no claim token, and a finished image is always wanted.
     */
    public void markReady(String assetKey, String imageUrl, String modelKey, ModelTier tier, boolean usedFallback) {
        mongoTemplate.updateFirst(
                query(where("visualizationKey").is(assetKey)),
                new Update()
                        .set("generationStatus", GlobalAssetStatus.READY)
                        .set("imageUrl", imageUrl)
                        .set("imageFailureReason", null)
                        .set("resolvedModelKey", modelKey)
                        .set("resolvedTier", tier)
                        .set("usedFallback", usedFallback)
                        .set("updatedAt", LocalDateTime.now())
                        .unset("leaseUntil"),
                VisualizationAsset.class);
    }

    /**
     * Marks the asset FAILED with {@code reason}, but only while {@code token} still owns it and
     * the image hasn't meanwhile been completed (e.g. by the recovery job).
     */
    public boolean markFailed(String assetKey, String token, String reason) {
        return mongoTemplate.updateFirst(
                query(where("visualizationKey").is(assetKey)
                        .and("generationToken").is(token)
                        .and("generationStatus").ne(GlobalAssetStatus.READY)),
                new Update()
                        .set("generationStatus", GlobalAssetStatus.FAILED)
                        .set("imageFailureReason", reason)
                        .set("updatedAt", LocalDateTime.now())
                        .unset("leaseUntil"),
                VisualizationAsset.class).getModifiedCount() > 0;
    }

    private Optional<VisualizationAsset> insertFirstClaim(
            GlobalResource resource, String assetKey, String token, Long requestedBy, Instant leaseUntil) {
        VisualizationAsset asset = new VisualizationAsset();
        asset.setId(sequenceGeneratorService.generateSequence(VisualizationAsset.SEQUENCE_NAME));
        asset.setVisualizationKey(assetKey);
        asset.setType(VisualizationAssetType.GLOBAL);
        asset.setResourceType(resource.type());
        asset.setResourceId(resource.id());
        asset.setGenerationStatus(GlobalAssetStatus.QUEUED);
        asset.setGenerationToken(token);
        asset.setLeaseUntil(leaseUntil);
        asset.setRequestedBy(requestedBy);
        LocalDateTime now = LocalDateTime.now();
        asset.setCreatedAt(now);
        asset.setUpdatedAt(now);
        try {
            return Optional.of(mongoTemplate.insert(asset));
        } catch (DuplicateKeyException e) {
            // A concurrent first request for the same resource won the insert race.
            return Optional.empty();
        }
    }
}
