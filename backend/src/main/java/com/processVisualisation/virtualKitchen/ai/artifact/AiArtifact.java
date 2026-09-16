package com.processVisualisation.virtualKitchen.ai.artifact;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A paid AI payload, persisted the instant the provider returned it and before any
 * dependent work was allowed to touch it.
 * <p>
 * This document exists to close one specific hole: an AI result that lives only in a
 * local variable between the provider call and the work that consumes it (uploading an
 * image to object storage, say) is destroyed by any failure in that consuming work, and
 * the only recovery is another paid generation. An artifact row makes the payload durable
 * across that window, so a retry reuses it instead of regenerating it.
 * <p>
 * Two invariants carry the design:
 * <ul>
 *   <li><b>{@code expiresAt} is null while {@link AiArtifactStatus#PENDING}.</b> MongoDB's
 *       TTL monitor skips documents whose indexed field is absent or non-date, so an
 *       artifact still waiting for its consumer can never expire, however long it waits.
 *       {@code expiresAt} is set only on the transition to {@code CONSUMED}/{@code ABANDONED},
 *       by which point the payload itself has already been deleted.</li>
 *   <li><b>{@code userId} is part of the dedup key.</b> Sharing a payload across users would
 *       leak one user's generated content to another and misattribute the billing that
 *       produced it. Two users with an identical prompt each pay once; that is the correct
 *       trade.</li>
 * </ul>
 * <p>
 * <b>Note on the TTL index:</b> this is the first TTL index in the codebase. MongoDB will not
 * alter an existing TTL index's options on a later boot, so changing the retention semantics
 * in {@code AiArtifactProperties} is <em>not</em> enough on its own — {@code artifact_ttl_idx}
 * must be dropped by hand so {@code spring.data.mongodb.auto-index-creation} can recreate it.
 * <p>
 * Timestamps are set explicitly on every write: {@code @EnableMongoAuditing} is not enabled
 * anywhere in this application, so {@code @CreatedDate}/{@code @LastModifiedDate} would be
 * silently null (as they already are on {@code VisualizationAsset}).
 *
 * @see AiArtifactService
 * @see AiArtifactRecoveryJob
 */
@Data
@Document(collection = "ai_artifacts")
@CompoundIndex(name = "artifact_dedup_idx", def = "{'userId': 1, 'artifactKey': 1}", unique = true)
@CompoundIndex(name = "artifact_sweep_idx", def = "{'status': 1, 'createdAt': 1}")
@CompoundIndex(name = "artifact_blob_gc_idx", def = "{'status': 1, 'gridFsId': 1}")
public class AiArtifact {

    @Id
    private String id;

    /** Owner of the generation, and half of the dedup key. */
    private Long userId;

    /** Deterministic fingerprint of the generation's <em>input</em> — see {@link AiArtifactKeyBuilder}. */
    private String artifactKey;

    private AiCapability capability;

    /** Matches the {@code AiRequestJob} correlation vocabulary, e.g. {@code "visualization-image"}. */
    private String correlationType;

    /** The domain object this payload belongs to, e.g. {@code recipeId::stepId}. */
    private String correlationId;

    private AiArtifactStatus status;

    private AiArtifactPayloadKind payloadKind;

    /** Which {@code AiArtifactCodec} can decode this payload; the sweeper resolves it by this id. */
    private String codecId;

    /** Which {@code AiArtifactConsumer} performs the dependent work; the sweeper resolves it by this id. */
    private String consumerId;

    private String contentType;

    private long payloadSize;

    /** Hex {@code ObjectId} of the GridFS blob; null for {@link AiArtifactPayloadKind#INLINE}. */
    private String gridFsId;

    /** Text/JSON payload; null for {@link AiArtifactPayloadKind#BINARY}. */
    private String inlinePayload;

    /**
     * Registry key of the model that produced this payload. Recorded as metadata and
     * deliberately <em>not</em> part of {@link #artifactKey}: a payload produced by a
     * fallback model is still a real, already-paid-for payload, and keying on the model
     * would orphan it the moment routing configuration changed.
     */
    private String producedByModelKey;

    private ModelTier producedByTier;

    private boolean usedFallback;

    private int creditCost;

    /** The {@code AiRequestJob.id} that was actually billed, so a reuse still points at the paid call. */
    private String producingJobId;

    /** How many times this payload was served instead of a new provider call. */
    private int reuseCount;

    private int recoveryAttempts;

    private String lastError;

    /**
     * Soft lease keeping the recovery sweeper away from an artifact a live request is
     * already consuming. Null or in the past means unclaimed. Expiry is the only release
     * mechanism, so a crashed holder cannot block recovery permanently.
     */
    private Instant leaseUntil;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant consumedAt;

    /**
     * TTL anchor. Null while {@link AiArtifactStatus#PENDING} — see the class javadoc for why
     * that is what makes the TTL index safe here.
     */
    @Indexed(name = "artifact_ttl_idx", expireAfter = "0s")
    private Instant expiresAt;
}
