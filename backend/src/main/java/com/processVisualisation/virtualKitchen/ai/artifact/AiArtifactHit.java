package com.processVisualisation.virtualKitchen.ai.artifact;

/**
 * A reusable artifact found by {@code AiArtifactService.findReusable}: the row, plus its
 * decoded payload. Returning one means no provider call was made and no credit was spent.
 *
 * @param artifact the artifact row, with its lease freshly taken
 * @param value the decoded payload
 * @param <R> the AI result type
 */
public record AiArtifactHit<R>(AiArtifact artifact, R value) {
}
