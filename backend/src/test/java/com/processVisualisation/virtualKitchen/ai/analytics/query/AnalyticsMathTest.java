package com.processVisualisation.virtualKitchen.ai.analytics.query;

import com.processVisualisation.virtualKitchen.ai.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.analytics.model.CostBasis;
import com.processVisualisation.virtualKitchen.ai.analytics.query.AnalyticsMath.GroupMetrics;
import com.processVisualisation.virtualKitchen.ai.analytics.query.AnalyticsMath.Row;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AnalyticsMathTest {

    private static Row row(String group, AnalyticsStatus status, Integer attempts, Integer transport, Double cost,
                           Long latency) {
        return new Row(Map.of("model", group), status, attempts, transport, cost,
                cost == null ? CostBasis.UNAVAILABLE : CostBasis.CALCULATED, cost, null, cost == null ? null : "USD",
                100L, 10L, latency, 5L, null, false);
    }

    @Test
    void requestCount_isLogicalRequests_attemptCountIncludesRetries() {
        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of(
                row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 0.01, 100L),
                row("a", AnalyticsStatus.SUCCEEDED, 3, 2, 0.03, 300L)));

        assertThat(m.requestCount()).isEqualTo(2);
        assertThat(m.attemptCount()).isEqualTo(4L);
        assertThat(m.retryRate()).isEqualTo(0.5);
        assertThat(m.retrySamples()).isEqualTo(2);
    }

    @Test
    void costStatistics_reportTheirSampleSizes() {
        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of(
                row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 0.01, 100L),
                row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 0.02, 200L),
                row("a", AnalyticsStatus.FAILED, 1, 1, 0.06, 300L)));

        assertThat(m.totalCost()).isCloseTo(0.09, within(1e-12));
        assertThat(m.avgCost().value()).isCloseTo(0.03, within(1e-12));
        assertThat(m.avgCost().samples()).isEqualTo(3);
        assertThat(m.medianCost().value()).isCloseTo(0.02, within(1e-12));
        assertThat(m.minCost()).isEqualTo(0.01);
        assertThat(m.maxCost()).isEqualTo(0.06);
        assertThat(m.costPerSuccess()).as("failed spend is included, divided by successes")
                .isCloseTo(0.045, within(1e-12));
        assertThat(m.failureRate()).isCloseTo(1 / 3.0, within(1e-12));
    }

    @Test
    void latencyPercentiles_useNearestRank() {
        List<Row> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            rows.add(row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 0.01, (long) i * 100));
        }
        GroupMetrics m = AnalyticsMath.summarize(Map.of(), rows);

        assertThat(m.p50LatencyMs().value()).isEqualTo(1000.0);
        assertThat(m.p95LatencyMs().value()).isEqualTo(1900.0);
        assertThat(m.p95LatencyMs().samples()).isEqualTo(20);
        assertThat(m.avgLatencyMs().value()).isEqualTo(1050.0);
    }

    @Test
    void missingCost_isNotTreatedAsZero() {
        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of(
                row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 0.04, 100L),
                row("a", AnalyticsStatus.SUCCEEDED, 1, 1, null, 100L)));

        assertThat(m.totalCost()).isEqualTo(0.04);
        assertThat(m.costedCount()).isEqualTo(1);
        assertThat(m.uncostedCount()).isEqualTo(1);
        assertThat(m.costComplete()).isFalse();
        assertThat(m.avgCost().value()).as("averaged over costed requests only").isEqualTo(0.04);
    }

    @Test
    void rejectedAndReusedRequests_haveNoMissingCost() {
        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of(
                row("a", AnalyticsStatus.REJECTED, null, null, null, null),
                row("a", AnalyticsStatus.REUSED, 0, 0, null, 10L)));

        assertThat(m.uncostedCount()).isZero();
        assertThat(m.totalCost()).isNull();
        assertThat(m.failureRate()).isNull();
    }

    @Test
    void mixedCurrencies_areNeverAdded() {
        Row usd = row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 1.0, 1L);
        Row eur = new Row(Map.of("model", "a"), AnalyticsStatus.SUCCEEDED, 1, 1, 1.0, CostBasis.CALCULATED, 1.0, null,
                "EUR", null, null, 1L, null, null, false);

        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of(usd, eur));

        assertThat(m.currencies()).containsExactly("USD", "EUR");
        assertThat(m.totalCost()).isNull();
        assertThat(m.avgCost().value()).isNull();
    }

    @Test
    void emptyDataset_hasNoMetrics() {
        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of());

        assertThat(m.requestCount()).isZero();
        assertThat(m.totalCost()).isNull();
        assertThat(m.avgLatencyMs().samples()).isZero();
        assertThat(m.retryRate()).isNull();
    }

    @Test
    void incompleteHistoricalData_isCounted() {
        Row historical = new Row(Map.of(), AnalyticsStatus.SUCCEEDED, null, 1, null, CostBasis.UNAVAILABLE, null, null,
                null, null, null, null, null, null, true);

        GroupMetrics m = AnalyticsMath.summarize(Map.of(), List.of(historical, row("a", AnalyticsStatus.SUCCEEDED, 1, 1, 0.1, 5L)));

        assertThat(m.historicalCount()).isEqualTo(1);
        assertThat(m.attemptSamples()).isEqualTo(1);
        assertThat(m.avgInputTokens().samples()).isEqualTo(1);
    }

    @Test
    void grouping_keepsGroupsApart_andWorkflowTotalsSumEachRequestOnce() {
        // Two workflows; each request carries its own cost exactly once.
        List<GroupMetrics> groups = AnalyticsMath.group(List.of(
                new Row(Map.of("workflow", "wf-1"), AnalyticsStatus.SUCCEEDED, 1, 1, 0.10, CostBasis.CALCULATED, 0.10, null, "USD", null, null, 1L, null, null, false),
                new Row(Map.of("workflow", "wf-1"), AnalyticsStatus.SUCCEEDED, 1, 1, 0.20, CostBasis.CALCULATED, 0.20, null, "USD", null, null, 1L, null, null, false),
                new Row(Map.of("workflow", "wf-2"), AnalyticsStatus.SUCCEEDED, 1, 1, 0.05, CostBasis.CALCULATED, 0.05, null, "USD", null, null, 1L, null, null, false)));

        assertThat(groups).hasSize(2);
        assertThat(groups.get(0).key()).containsEntry("workflow", "wf-1");
        assertThat(groups.get(0).totalCost()).isCloseTo(0.30, within(1e-12));
        assertThat(groups.get(1).totalCost()).isCloseTo(0.05, within(1e-12));
    }

    @Test
    void criteria_applyEveryFilterInTheDatabaseQuery() {
        AnalyticsFilter filter = new AnalyticsFilter();
        filter.setFrom(Instant.parse("2026-10-01T00:00:00Z"));
        filter.setOperationContext(List.of("RECIPE_FLOW_GENERATION"));
        filter.setModel(List.of("gemini-flash", " "));
        filter.setCostMin(0.01);
        filter.setLatencyMax(5000L);
        filter.setRetryMin(1);
        filter.setRecipeId("55");

        // Query.toString serializes values (such as Instant) that a raw Document cannot.
        String json = new Query(AnalyticsCriteria.build(filter)).toString();

        assertThat(json).contains("createdAt").contains("RECIPE_FLOW_GENERATION").contains("gemini-flash")
                .contains("totalCost").contains("totalMs").contains("transportAttempts").contains("recipeId");
        assertThat(new Query(AnalyticsCriteria.build(new AnalyticsFilter())).getQueryObject()).isEmpty();
    }
}
