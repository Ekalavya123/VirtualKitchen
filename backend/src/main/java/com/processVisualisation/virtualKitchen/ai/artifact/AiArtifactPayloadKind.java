package com.processVisualisation.virtualKitchen.ai.artifact;

/**
 * Where an {@link AiArtifact}'s payload physically lives. Chosen by the
 * {@code AiArtifactCodec} for the payload type, except that an {@link #INLINE}
 * payload larger than {@code ai.artifact.max-inline-bytes} is promoted to
 * {@link #BINARY} so a runaway response can never push the artifact document
 * toward MongoDB's 16 MB limit.
 */
public enum AiArtifactPayloadKind {

    /** Stored in GridFS; the artifact row holds only {@code gridFsId}. */
    BINARY,

    /** Stored directly in the artifact document's {@code inlinePayload} field. */
    INLINE
}
