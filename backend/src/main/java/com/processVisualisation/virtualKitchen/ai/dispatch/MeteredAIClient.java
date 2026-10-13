package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

import java.time.Instant;

/**
 * Wraps a provider {@link AIClient} so every successful response's token usage is counted against the AI request
 * job running on the current thread (see {@link AiUsageMeter}). Failed calls return no usage, so they are not
 * counted in the job's totals; every call, failed or not, is still handed to the job's analytics listener.
 */
public class MeteredAIClient implements AIClient {

    private final AIClient delegate;
    private final String provider;

    public MeteredAIClient(AIClient delegate, String provider) {
        this.delegate = delegate;
        this.provider = provider;
    }

    @Override
    public AIResponse chat(AIRequest request) {
        Instant startedAt = Instant.now();
        long startedAtNanos = System.nanoTime();
        AIResponse response;
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

    private AiCallRecord.AiCallRecordBuilder callRecord(AIRequest request, Instant startedAt, long startedAtNanos) {
        int inputChars = length(request.getSystemPrompt()) + length(request.getUserPrompt());
        return AiCallRecords.started(request.getOperation(), provider, request.getModel(), startedAt, startedAtNanos)
                .inputChars(inputChars)
                .inputText(request.getUserPrompt());
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }
}
