package com.processVisualisation.virtualKitchen.ai.dispatch;

import com.processVisualisation.virtualKitchen.ai.usage.AiCallListener;
import com.processVisualisation.virtualKitchen.ai.usage.AiCallRecord;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsage;
import com.processVisualisation.virtualKitchen.ai.usage.AiUsageMeter;
import com.processVisualisation.virtualKitchen.restclient.client.ImageGenerationClient;
import com.processVisualisation.virtualKitchen.restclient.client.ProviderUsage;
import com.processVisualisation.virtualKitchen.restclient.client.tts.AudioFormat;
import com.processVisualisation.virtualKitchen.restclient.client.tts.GeneratedAudio;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsProvider;
import com.processVisualisation.virtualKitchen.restclient.client.tts.TtsRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIRequest;
import com.processVisualisation.virtualKitchen.restclient.dto.AIResponse;
import com.processVisualisation.virtualKitchen.restclient.exception.AITimeoutException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Every provider call made through the dispatch wrappers reaches the request's analytics listener. */
class AnalyticsCallRecordingTest {

    private final List<AiCallRecord> calls = new CopyOnWriteArrayList<>();
    private final AiUsage usage = new AiUsage();

    {
        usage.setCallListener(new AiCallListener() {
            @Override
            public void onCall(AiCallRecord record) {
                calls.add(record);
            }
        });
    }

    @Test
    void textCall_recordsReportedUsageModelAndOutput() throws Exception {
        MeteredAIClient client = new MeteredAIClient(request -> AIResponse.builder().content("hello").model("gemini-x-001")
                .promptTokens(10).completionTokens(5).totalTokens(15).cachedTokens(4).finishReason("STOP").build(), "gemini");

        AiUsageMeter.measure(usage, () -> client.chat(AIRequest.builder().model("gemini-x").userPrompt("hi")
                .systemPrompt("sys").operation("CHAT").build()));

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.isSuccess()).isTrue();
            assertThat(call.getPurpose()).isEqualTo("CHAT");
            assertThat(call.getProviderModelId()).isEqualTo("gemini-x");
            assertThat(call.getReportedModel()).isEqualTo("gemini-x-001");
            assertThat(call.getInputTokens()).isEqualTo(10L);
            assertThat(call.getCachedInputTokens()).isEqualTo(4L);
            assertThat(call.getOutputText()).isEqualTo("hello");
            assertThat(call.getInputChars()).isEqualTo(5);
        });
        assertThat(usage.summarize(null).providerCalls()).as("existing metering unchanged").isEqualTo(1);
    }

    @Test
    void failedTextCall_isRecordedAndTheErrorRethrownUnchanged() {
        AITimeoutException timeout = new AITimeoutException("read timed out");
        MeteredAIClient client = new MeteredAIClient(request -> {
            throw timeout;
        }, "gemini");

        assertThatThrownBy(() -> AiUsageMeter.measure(usage, () -> client.chat(AIRequest.builder().model("m").build())))
                .isSameAs(timeout);
        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.isSuccess()).isFalse();
            assertThat(call.getErrorType()).isEqualTo("AITimeoutException");
            assertThat(call.getInputTokens()).isNull();
        });
        assertThat(usage.summarize(null).providerCalls()).as("failed calls still not metered").isZero();
    }

    @Test
    void imageCall_withoutTokens_recordsImageMetadataAndNoUsage() throws Exception {
        ImageGenerationClient local = prompt -> new ImageGenerationClient.GeneratedImage("image/png", new byte[]{1, 2, 3},
                ProviderUsage.NONE.withImage(1, 1024, 768));
        LoggingImageGenerationClient client = new LoggingImageGenerationClient(local, "drawThings", "sdxl");

        AiUsageMeter.measure(usage, () -> client.generate("a pan"));

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.getImageCount()).isEqualTo(1);
            assertThat(call.getImageWidth()).isEqualTo(1024);
            assertThat(call.getOutputBytes()).isEqualTo(3L);
            assertThat(call.getInputTokens()).isNull();
        });
    }

    @Test
    void speechCall_recordsDurationAndTheModelThatActuallySpoke() throws Exception {
        TtsProvider provider = new TtsProvider() {
            @Override
            public String providerName() {
                return "gemini";
            }

            @Override
            public GeneratedAudio synthesize(TtsRequest request) {
                return new GeneratedAudio(new byte[10], "audio/wav", AudioFormat.WAV, 2500L, "Kore", "tts-model-002",
                        new ProviderUsage(20L, 400L, null, 420L));
            }
        };
        LoggingTtsProvider client = new LoggingTtsProvider(provider, "gemini-tts");

        AiUsageMeter.measure(usage, () -> client.synthesize(new TtsRequest("Stir well", "tts-model", "Kore", "en-US", null)));

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.getAudioDurationMs()).isEqualTo(2500L);
            assertThat(call.getReportedModel()).isEqualTo("tts-model-002");
            assertThat(call.getOutputTokens()).isEqualTo(400L);
            assertThat(call.getInputChars()).isEqualTo(9);
        });
    }

    @Test
    void callsOutsideATrackedRequest_areIgnored() {
        MeteredAIClient client = new MeteredAIClient(request -> AIResponse.builder().content("x").build(), "gemini");

        client.chat(AIRequest.builder().build());

        assertThat(calls).isEmpty();
    }
}
