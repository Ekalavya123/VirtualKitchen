package com.processVisualisation.virtualKitchen.ai.platform.usage;

import com.processVisualisation.virtualKitchen.ai.provider.ProviderCallReporter.ProviderCall;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MeteredProviderCallReporterTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void sideCallIsRecordedAgainstTheRequestBoundToTheThread() throws Exception {
        List<AiCallRecord> calls = new ArrayList<>();
        AiUsage usage = new AiUsage();
        usage.setCallListener(calls::add);

        AiUsageMeter.measure(usage, () -> {
            new MeteredProviderCallReporter().report(new ProviderCall("PROMPT_CACHE_CREATE", "gemini", "gemini-pro",
                    T0, T0.plusMillis(40), 40, true, 1200, 300L, null, null));
            return null;
        });

        assertThat(calls).singleElement().satisfies(call -> {
            assertThat(call.getPurpose()).isEqualTo("PROMPT_CACHE_CREATE");
            assertThat(call.getProviderModelId()).isEqualTo("gemini-pro");
            assertThat(call.getDurationMs()).isEqualTo(40);
            assertThat(call.isSuccess()).isTrue();
            assertThat(call.getInputChars()).isEqualTo(1200);
            assertThat(call.getTotalTokens()).isEqualTo(300L);
        });
    }

    @Test
    void sideCallOutsideARequestIsIgnored() {
        new MeteredProviderCallReporter().report(new ProviderCall("PROMPT_CACHE_CREATE", "gemini", "gemini-pro",
                T0, T0, 0, false, 10, null, "IllegalStateException", "boom"));
    }
}
