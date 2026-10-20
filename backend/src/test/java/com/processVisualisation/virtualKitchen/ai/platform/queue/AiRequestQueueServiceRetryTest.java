package com.processVisualisation.virtualKitchen.ai.platform.queue;

import com.processVisualisation.virtualKitchen.ai.platform.artifact.AiArtifactService;
import com.processVisualisation.virtualKitchen.ai.platform.credit.CreditService;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiCapability;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiModelProperties;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiModelRegistry;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelTier;
import com.processVisualisation.virtualKitchen.ai.platform.routing.ModelSelectionService;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiCommunicationException;
import com.processVisualisation.virtualKitchen.ai.provider.exception.AiRateLimitedException;
import com.processVisualisation.virtualKitchen.common.concurrent.ThreadPoolTaskPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A bulk narration fires one TTS request per step; past Gemini's requests-per-minute limit the
 * provider answers 429. Those requests must wait and retry rather than fail the step after a
 * single 500ms pause, while ordinary communication failures keep their one quick retry.
 */
class AiRequestQueueServiceRetryTest {

    private static final String MODEL_KEY = "test-tts";

    private AiRequestQueueService queueService;
    private AiRequestProperties requestProperties;
    private final List<Long> sleeps = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ModelDefinition tts = new ModelDefinition();
        tts.setKey(MODEL_KEY);
        tts.setCapability(AiCapability.TEXT_TO_SPEECH);
        tts.setTier(ModelTier.OPEN_SOURCE);
        tts.setProviderBean("testTts");
        tts.setProviderModelId("test-tts-model");
        tts.setCreditCost(0);
        tts.setEnabled(true);

        AiModelProperties modelProperties = new AiModelProperties();
        modelProperties.setModels(List.of(tts));
        modelProperties.setDefaultModel(Map.of(AiCapability.TEXT_TO_SPEECH.name(), MODEL_KEY));
        AiModelRegistry modelRegistry = new AiModelRegistry(modelProperties);

        CreditService creditService = mock(CreditService.class);
        AiRequestJobRepository jobRepository = mock(AiRequestJobRepository.class);
        when(jobRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        requestProperties = new AiRequestProperties();
        queueService = new AiRequestQueueService(
                jobRepository,
                new ModelSelectionService(modelRegistry, creditService),
                creditService,
                new ThreadPoolTaskPool(1, "test-ai-text"),
                new AiQueueProperties(),
                requestProperties,
                mock(AiArtifactService.class),
                modelRegistry);
        queueService.setSleeper(sleeps::add);
    }

    @Test
    void throttledRequestWaitsAndRetriesUntilItSucceeds() {
        AtomicInteger calls = new AtomicInteger();

        AiRequestOutcome<String> outcome = run(() -> {
            if (calls.incrementAndGet() <= 4) {
                throw new AiRateLimitedException("Gemini TTS rate limit or quota exceeded (429)", null, null);
            }
            return "audio";
        });

        assertEquals("audio", outcome.value());
        assertEquals(5, calls.get());
        assertEquals(4, sleeps.size());
        // Exponential from 5s (+ up to 20% jitter), capped at 45s.
        assertBetween(5_000, 6_000, sleeps.get(0));
        assertBetween(10_000, 12_000, sleeps.get(1));
        assertBetween(20_000, 24_000, sleeps.get(2));
        assertBetween(40_000, 45_000, sleeps.get(3));
    }

    @Test
    void throttledRequestHonoursTheProvidersRetryDelay() {
        AtomicInteger calls = new AtomicInteger();

        run(() -> {
            if (calls.incrementAndGet() == 1) {
                throw new AiRateLimitedException("429", Duration.ofSeconds(30), null);
            }
            return "audio";
        });

        assertEquals(1, sleeps.size());
        assertBetween(30_000, 36_000, sleeps.get(0));
    }

    @Test
    void throttledRequestGivesUpAfterItsRetryBudget() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(AiRateLimitedException.class, () -> run(() -> {
            calls.incrementAndGet();
            throw new AiRateLimitedException("429", Duration.ofSeconds(1), null);
        }));

        assertEquals(1 + requestProperties.getRateLimitMaxRetries(), calls.get());
    }

    @Test
    void ordinaryCommunicationFailureKeepsItsSingleQuickRetry() {
        AtomicInteger calls = new AtomicInteger();

        assertThrows(AiCommunicationException.class, () -> run(() -> {
            calls.incrementAndGet();
            throw new AiCommunicationException("boom");
        }));

        assertEquals(2, calls.get());
        assertEquals(List.of(requestProperties.getRetryBackoffMs()), sleeps);
    }

    private AiRequestOutcome<String> run(java.util.function.Supplier<String> provider) {
        return queueService.executeInline(1L, AiCapability.TEXT_TO_SPEECH, null, null,
                "step-narration", "1::step", selection -> provider.get());
    }

    private static void assertBetween(long min, long max, long actual) {
        assertTrue(actual >= min && actual <= max, "expected " + min + ".." + max + " but was " + actual);
    }
}
