package com.processVisualisation.virtualKitchen.ai.artifact;

import java.nio.charset.StandardCharsets;

/**
 * An encoded {@link AiArtifact} payload on its way to or from storage. Produced and
 * consumed by {@code AiArtifactCodec} implementations; {@code AiArtifactService} decides
 * only <em>where</em> it lands, never how it is shaped.
 *
 * @param kind whether this payload belongs in GridFS or inline in the artifact document
 * @param contentType media type of the payload, preserved across the round trip
 * @param binary the bytes, for {@link AiArtifactPayloadKind#BINARY}; null otherwise
 * @param text the text, for {@link AiArtifactPayloadKind#INLINE}; null otherwise
 */
public record AiArtifactPayload(
        AiArtifactPayloadKind kind,
        String contentType,
        byte[] binary,
        String text
) {

    /** A payload that belongs in GridFS. */
    public static AiArtifactPayload binary(String contentType, byte[] data) {
        return new AiArtifactPayload(AiArtifactPayloadKind.BINARY, contentType, data, null);
    }

    /**
     * A payload that belongs inline in the artifact document. Note that
     * {@code AiArtifactService} may still promote it to GridFS if it exceeds
     * {@code ai.artifact.max-inline-bytes}; {@link #asText()} makes that transparent
     * on the way back.
     */
    public static AiArtifactPayload inline(String contentType, String text) {
        return new AiArtifactPayload(AiArtifactPayloadKind.INLINE, contentType, null, text);
    }

    /**
     * The payload as bytes, regardless of how it was stored — so a codec need not care
     * whether its inline payload was promoted to GridFS.
     */
    public byte[] asBytes() {
        if (binary != null) {
            return binary;
        }
        return text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
    }

    /** The payload as text, regardless of how it was stored. */
    public String asText() {
        if (text != null) {
            return text;
        }
        return binary == null ? "" : new String(binary, StandardCharsets.UTF_8);
    }

    /** Byte length of the payload as it will be stored. */
    public long size() {
        return asBytes().length;
    }
}
