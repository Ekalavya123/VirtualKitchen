package com.processVisualisation.virtualKitchen.recipe.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Provider-independent narration state for one recipe step, as consumed by the slideshow player.
 * {@code audioUrl} is set only when {@code status} is {@code READY}: stale audio is never handed out.
 */
@Data
@Builder
public class StepNarrationResponseDTO {
    private String stepId;
    /** NOT_GENERATED, GENERATING, READY, STALE or FAILED. */
    private String status;
    /** False when the step has no text to speak; such a step never generates narration. */
    private boolean narratable;
    private String audioUrl;
    private String mimeType;
    private Double durationSeconds;
    private String provider;
    private String modelKey;
    private String voice;
    private String languageCode;
    private Instant generatedAt;
    private String failureReason;
    /** For FAILED: when an automatic retry is allowed again (a forced retry is always allowed). */
    private Instant retryAfter;
}
