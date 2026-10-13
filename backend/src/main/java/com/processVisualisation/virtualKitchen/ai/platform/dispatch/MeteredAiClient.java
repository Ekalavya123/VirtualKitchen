package com.processVisualisation.virtualKitchen.ai.platform.dispatch;

import com.processVisualisation.virtualKitchen.ai.platform.usage.AiCallRecord;
import com.processVisualisation.virtualKitchen.ai.platform.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.ai.provider.AiClient;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiRequest;
import com.processVisualisation.virtualKitchen.ai.provider.dto.AiResponse;

import java.time.Instant;

/**
 * Wraps a provider {@link AiClient} so every successful response's token usage is counted against the AI request
 * job running on the current thread (see {@link AiUsageMeter}). Failed calls return no usage, so they are not
 * counted in the job's totals; every call, failed or not, is still handed to the job's analytics listener.
 */
public class MeteredAiClient implements AiClient {

    private final AiClient delegate;
    private final String provider;

    public MeteredAiClient(AiClient delegate, String provider) {
        this.delegate = delegate;
        this.provider = provider;
    }

    @Override
    public AiResponse chat(AiRequest request) {
        Instant startedAt = Instant.now();
        long startedAtNanos = System.nanoTime();
        AiResponse response;
        try {
            response = delegate.chat(request);
        } catch (RuntimeException ex) {
            AiUsageMeter.recordCall(AiCallRecords.failed(callRecord(request, startedAt, startedAtNanos), ex));
            throw ex;
        }
        AiUsageMeter.record(response);
        AiCallRecord.AiCallRecordBuilder record = callRecord(request, startedAt, startedAtNanos).success(true);
        if (response != null) {
            record.reportedModel(response.getModel())
                    .inputTokens(AiCallRecords.toLong(response.getPromptTokens()))
                    .cachedInputTokens(AiCallRecords.toLong(response.getCachedTokens()))
                    .outputTokens(AiCallRecords.toLong(response.getCompletionTokens()))
                    .reasoningTokens(AiCallRecords.toLong(response.getThoughtsTokens()))
                    .totalTokens(AiCallRecords.toLong(response.getTotalTokens()))
                    .finishReason(response.getFinishReason())
                    .providerDurationMs(response.getProviderDurationMs())
                    .outputChars(response.getContent() == null ? 0 : response.getContent().length())
                    .outputText(response.getContent());
        }
        AiUsageMeter.recordCall(record.build());
        return response;
    }

    private AiCallRecord.AiCallRecordBuilder callRecord(AiRequest request, Instant startedAt, long startedAtNanos) {
        int inputChars = length(request.getSystemPrompt()) + length(request.getUserPrompt());
        return AiCallRecords.started(request.getOperation(), provider, request.getModel(), startedAt, startedAtNanos)
                .inputChars(inputChars)
                .inputText(request.getUserPrompt());
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}
