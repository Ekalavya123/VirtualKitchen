package com.processVisualisation.virtualKitchen.ai.usage;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * One provider call as observed by the dispatch wrappers: who served it, how long it took, what the provider
 * reported, and whether it failed. Every count is null when the provider did not report it.
 * <p>
 * {@code inputText}/{@code outputText} carry the prompt and generated text of a text call only so the analytics
 * recorder can retain the output in the existing {@code ai_responses} store; they are never copied into analytics.
 */
@Value
@Builder(toBuilder = true)
public class AiCallRecord {

    String purpose;
    String provider;
    String providerModelId;
    String reportedModel;

    Instant startedAt;
    Instant endedAt;
    long durationMs;

    boolean success;
    String errorType;
    Integer httpStatus;
    String errorMessage;
    String finishReason;

    Long inputTokens;
    Long cachedInputTokens;
    Long outputTokens;
    Long reasoningTokens;
    Long totalTokens;

    Integer imageCount;
    Integer imageWidth;
    Integer imageHeight;
    Long audioDurationMs;
    Integer inputChars;
    Integer outputChars;
    Long outputBytes;
    Long providerDurationMs;

    String inputText;
    String outputText;
}
