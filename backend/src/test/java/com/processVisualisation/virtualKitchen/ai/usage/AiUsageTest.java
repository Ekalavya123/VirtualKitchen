package com.processVisualisation.virtualKitchen.ai.usage;

import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Cost estimation from provider token counts, and per-thread attribution of responses to the running job. */
class AiUsageTest {

    @Test
    void sumsAllCallsAndPricesCachedInputAndThinkingCorrectly() {
        AiUsage usage = new AiUsage();
        // First call: 10,000 prompt tokens of which 9,000 came from the cache; 1,000 output + 2,000 thinking.
        usage.add(response(10_000, 9_000, 1_000, 2_000));
        // Validation retry: nothing cached.
        usage.add(response(12_000, null, 1_000, null));

        AiUsageSummary summary = usage.summarize(model(0.25, 1.50, 0.025));

        assertEquals(2, summary.providerCalls());
        assertEquals(22_000, summary.promptTokens());
        assertEquals(9_000, summary.cachedTokens());
        assertEquals(2_000, summary.completionTokens());
        assertEquals(2_000, summary.thoughtsTokens());
        // uncached 13,000 x 0.25 + cached 9,000 x 0.025 + output (2,000 + 2,000) x 1.50, per 1M tokens
        double expected = (13_000 * 0.25 + 9_000 * 0.025 + 4_000 * 1.50) / 1_000_000;
        assertEquals(expected, summary.estimatedCostUsd(), 1e-12);
    }

    @Test
    void cachedTokensFallBackToTheInputPriceAndUnpricedModelsHaveNoCost() {
        AiUsage usage = new AiUsage();
        usage.add(response(1_000, 400, 100, null));

        assertEquals((1_000 * 2.0 + 100 * 8.0) / 1_000_000, usage.summarize(model(2.0, 8.0, null)).estimatedCostUsd(), 1e-12);
        assertNull(usage.summarize(model(null, null, null)).estimatedCostUsd());
        assertEquals(0.0, usage.summarize(model(0.0, 0.0, null)).estimatedCostUsd());
    }

    @Test
    void meterCountsOnlyResponsesInsideTheBoundJob() throws Exception {
        AiUsage usage = new AiUsage();

        AiUsageMeter.record(response(5, null, 5, null)); // not inside a job: ignored
        AiUsageMeter.measure(usage, () -> {
            AiUsageMeter.record(response(100, null, 10, null));
            return null;
        });
        AiUsageMeter.record(response(5, null, 5, null)); // binding was cleared afterwards

        assertEquals(1, usage.summarize(null).providerCalls());
        assertEquals(100, usage.summarize(null).promptTokens());
    }

    private static AIResponse response(Integer prompt, Integer cached, Integer completion, Integer thoughts) {
        return AIResponse.builder()
                .promptTokens(prompt)
                .cachedTokens(cached)
                .completionTokens(completion)
                .thoughtsTokens(thoughts)
                .build();
    }

    private static ModelDefinition model(Double input, Double output, Double cachedInput) {
        ModelDefinition model = new ModelDefinition();
        model.setInputUsdPerMillion(input);
        model.setOutputUsdPerMillion(output);
        model.setCachedInputUsdPerMillion(cachedInput);
        return model;
    }
}
