package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;
import com.processVisualisation.virtualKitchen.restclient.client.ProviderUsage;

import java.time.Instant;

/** Builds the {@link AiCallRecord}s the dispatch wrappers hand to {@code AiUsageMeter.recordCall}. */
final class AiCallRecords {

    private AiCallRecords() {
    }

    /** A record skeleton for a call that started at {@code startedAt} and has just ended. */
    static AiCallRecord.AiCallRecordBuilder started(String purpose, String provider, String providerModelId,
                                                    Instant startedAt, long startedAtNanos) {
        return AiCallRecord.builder()
                .purpose(purpose)
                .provider(provider)
                .providerModelId(providerModelId)
                .startedAt(startedAt)
                .endedAt(Instant.now())
                .durationMs(LoggingAIClient.elapsedMs(startedAtNanos));
    }

    static AiCallRecord failed(AiCallRecord.AiCallRecordBuilder builder, Exception ex) {
        return builder.success(false)
                .errorType(ex.getClass().getSimpleName())
                .httpStatus(LoggingAIClient.httpStatusOf(ex))
                .errorMessage(ex.getMessage())
                .build();
    }

    /** Copies what an image/speech provider reported; counts it did not report stay null. */
    static AiCallRecord.AiCallRecordBuilder withUsage(AiCallRecord.AiCallRecordBuilder builder, ProviderUsage usage) {
        if (usage == null) {
            return builder;
        }
        return builder.inputTokens(usage.inputTokens())
                .cachedInputTokens(usage.cachedTokens())
                .outputTokens(usage.outputTokens())
                .reasoningTokens(usage.thoughtsTokens())
                .totalTokens(usage.totalTokens())
                .reportedModel(usage.reportedModel())
                .imageCount(usage.imageCount())
                .imageWidth(usage.imageWidth())
                .imageHeight(usage.imageHeight());
    }

    static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }
}
