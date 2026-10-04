package com.processVisualisation.virtualKitchen.ai.usage;

import com.processVisualisation.virtualKitchen.ai.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.restclient.client.ProviderUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiCostLedgerTest {

    @Test
    void pricesImageAndSpeechCallsFromTheirReportedTokens() {
        AiUsage usage = new AiUsage();
        usage.add(new ProviderUsage(85L, 400L, null, 485L)); // e.g. Gemini TTS: text in, audio tokens out

        AiUsageSummary summary = usage.summarize(priced(0.50, 10.00));

        assertThat(summary.providerCalls()).isEqualTo(1);
        assertThat(summary.promptTokens()).isEqualTo(85);
        assertThat(summary.completionTokens()).isEqualTo(400);
        assertThat(summary.estimatedCostUsd()).isCloseTo((85 * 0.50 + 400 * 10.00) / 1_000_000d,
                org.assertj.core.data.Offset.offset(1e-12));
    }

    @Test
    void aProviderThatReportsNothingStillCountsAsACallAndCostsZeroWhenFree() {
        AiUsage usage = new AiUsage();
        usage.add(ProviderUsage.NONE);

        AiUsageSummary summary = usage.summarize(priced(0, 0));

        assertThat(summary.providerCalls()).isEqualTo(1);
        assertThat(summary.estimatedCostUsd()).isZero();
    }

    @Test
    void sumsEveryRequestOfAnOperationAndForgetsItOnDrain() {
        AiCostLedger.record("op-1", new AiUsageSummary(1, 100, 0, 50, 10, 160, 0.002));
        AiCostLedger.record("op-1", new AiUsageSummary(1, 20, 0, 1290, 0, 1310, 0.0387));
        AiCostLedger.record("op-2", new AiUsageSummary(1, 5, 0, 5, 0, 10, 0.5));

        AiCostLedger.Totals totals = AiCostLedger.drain("op-1");

        assertThat(totals.requests()).isEqualTo(2);
        assertThat(totals.inputTokens()).isEqualTo(120);
        assertThat(totals.outputTokens()).isEqualTo(50 + 10 + 1290);
        assertThat(totals.costUsd()).isCloseTo(0.0407, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(totals.unpricedRequests()).isZero();
        assertThat(AiCostLedger.drain("op-1")).isEqualTo(AiCostLedger.Totals.NONE);
        AiCostLedger.drain("op-2");
    }

    @Test
    void unpricedRequestsMakeTheCostALowerBound() {
        AiCostLedger.record("op-3", new AiUsageSummary(1, 10, 0, 10, 0, 20, 0.001));
        AiCostLedger.record("op-3", new AiUsageSummary(1, 10, 0, 10, 0, 20, null));

        String fields = AiCostLedger.drain("op-3").toLogFields();

        assertThat(fields).contains("aiRequests=2").contains("costUsd=>=0.001000").contains("costInr=>=");
    }

    @Test
    void requestsOutsideAnyOperationAreIgnored() {
        AiCostLedger.record(null, new AiUsageSummary(1, 1, 0, 1, 0, 2, 1.0));
        assertThat(AiCostLedger.drain(null)).isEqualTo(AiCostLedger.Totals.NONE);
    }

    private static ModelDefinition priced(double input, double output) {
        ModelDefinition model = new ModelDefinition();
        model.setKey("m");
        model.setInputUsdPerMillion(input);
        model.setOutputUsdPerMillion(output);
        return model;
    }
}
