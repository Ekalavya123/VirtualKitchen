package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.pricing.PricingRateCard;
import com.processVisualisation.virtualKitchen.ai.analytics.pricing.PricingRegistry;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Shared builders for analytics tests: a recorder over in-memory fakes, models and provider-call records. */
public final class AnalyticsTestSupport {

    public static final String PAID_TEXT = "gemini-flash";
    public static final String LOCAL_TEXT = "ollama-qwen";

    private AnalyticsTestSupport() {
    }

    /** A recorder over the given store; the pricing registry prices {@link #PAID_TEXT} and {@link #LOCAL_TEXT}. */
    public static Fixture fixture() {
        InMemoryAnalyticsStore store = new InMemoryAnalyticsStore();
        RecordingTextSink sink = new RecordingTextSink();
        PricingRegistry pricing = mock(PricingRegistry.class);
        when(pricing.rateFor(any(), any())).thenReturn(Optional.empty());
        when(pricing.rateFor(eq(PAID_TEXT), any())).thenReturn(Optional.of(paidCard()));
        when(pricing.rateFor(eq(LOCAL_TEXT), any())).thenReturn(Optional.of(localCard()));
        // A real resolver over an empty database: ids parsed from correlation ids, nothing found by lookup.
        CorrelationResolver resolver = new CorrelationResolver(mock(MongoTemplate.class));
        AnalyticsHealth health = new AnalyticsHealth();
        AiAnalyticsRecorder recorder = new AiAnalyticsRecorder(store, pricing, sink, resolver, health);
        return new Fixture(store, sink, health, recorder);
    }

    public record Fixture(InMemoryAnalyticsStore store, RecordingTextSink sink, AnalyticsHealth health,
                          AiAnalyticsRecorder recorder) {
    }

    public static PricingRateCard paidCard() {
        PricingRateCard card = new PricingRateCard();
        card.setId(PAID_TEXT + "@v1");
        card.setVersion("v1");
        card.setModelKey(PAID_TEXT);
        card.setTier("PAID");
        card.setInputPerMTok(0.25);
        card.setCachedInputPerMTok(0.025);
        card.setOutputPerMTok(1.50);
        card.setCurrency("USD");
        return card;
    }

    public static PricingRateCard localCard() {
        PricingRateCard card = new PricingRateCard();
        card.setId(LOCAL_TEXT + "@v1");
        card.setVersion("v1");
        card.setModelKey(LOCAL_TEXT);
        card.setTier("OPEN_SOURCE");
        card.setInputPerMTok(0d);
        card.setOutputPerMTok(0d);
        card.setCurrency("USD");
        return card;
    }

    public static ModelDefinition model(String key, ModelTier tier, AiCapability capability) {
        ModelDefinition model = new ModelDefinition();
        model.setKey(key);
        model.setTier(tier);
        model.setCapability(capability);
        model.setProviderBean(tier == ModelTier.PAID ? "geminiAiClient" : "ollamaAiClient");
        model.setProviderModelId(key + "-id");
        model.setCreditCost(tier == ModelTier.PAID ? 1 : 0);
        model.setEnabled(true);
        return model;
    }

    public static AiCallRecord textCall(String purpose, long input, long output, String content) {
        return AiCallRecord.builder()
                .purpose(purpose)
                .provider("gemini")
                .providerModelId("gemini-flash-id")
                .reportedModel("gemini-flash-001")
                .startedAt(Instant.parse("2026-10-11T10:00:00Z"))
                .endedAt(Instant.parse("2026-10-11T10:00:02Z"))
                .durationMs(2000)
                .success(true)
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(input + output)
                .outputText(content)
                .inputText("the prompt")
                .build();
    }

    public static AiCallRecord failedCall(String errorType, Integer status, String message) {
        return AiCallRecord.builder()
                .purpose("RECIPE_TO_FLOW")
                .provider("gemini")
                .providerModelId("gemini-flash-id")
                .startedAt(Instant.parse("2026-10-11T10:00:00Z"))
                .endedAt(Instant.parse("2026-10-11T10:00:01Z"))
                .durationMs(1000)
                .success(false)
                .errorType(errorType)
                .httpStatus(status)
                .errorMessage(message)
                .build();
    }
}
