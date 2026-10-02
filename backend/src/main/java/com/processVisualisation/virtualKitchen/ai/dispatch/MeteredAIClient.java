package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.ai.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.restclient.client.AIClient;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;

/**
 * Wraps a provider {@link AIClient} so every successful response's token usage is counted against the AI request
 * job running on the current thread (see {@link AiUsageMeter}). Failed calls return no usage, so they are not counted.
 */
class MeteredAIClient implements AIClient {

    private final AIClient delegate;

    MeteredAIClient(AIClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public AIResponse chat(AIRequest request) {
        AIResponse response = delegate.chat(request);
        AiUsageMeter.record(response);
        return response;
    }
}
