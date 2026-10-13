package com.processVisualisation.virtualKitchen.ai.platform.analytics;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Attempt;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Cost;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics.Usage;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.CostBasis;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.DataCompleteness;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.UsageBasis;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Recomputes a live record's roll-ups from its attempts. Always a full recomputation rather than an increment, so
 * applying the same attempt twice, or recomputing after a late attempt, can never double-count.
 * <p>
 * A sum is null when no attempt reported that figure. A request's cost is the sum of its costed attempts, and is
 * marked incomplete when any attempt could not be costed.
 */
public final class AnalyticsTotals {

    private AnalyticsTotals() {
    }

    public static void recompute(AiRequestAnalytics record) {
        List<Attempt> attempts = record.getAttempts() == null ? List.of() : record.getAttempts();
        record.setAttemptCount(attempts.size());
        record.setFailedAttemptCount((int) attempts.stream().filter(a -> a.getStatus() == AnalyticsStatus.FAILED).count());
        record.setUsageTotals(attempts.isEmpty() ? null : sumUsage(attempts.stream().map(Attempt::getUsage).toList()));
        Cost cost = attempts.isEmpty() ? null : sumCost(attempts.stream().map(Attempt::getCost).toList());
        record.setCostTotals(cost);
        record.setTotalCost(cost == null ? null : cost.getTotalCost());

        attempts.stream()
                .filter(a -> a.getStatus() == AnalyticsStatus.SUCCEEDED)
                .reduce((first, second) -> second)
                .or(() -> attempts.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(attempts.get(attempts.size() - 1)))
                .ifPresent(last -> {
                    record.setFinalProvider(last.getProvider());
                    record.setFinalProviderModelId(last.getReportedModel() != null ? last.getReportedModel() : last.getProviderModelId());
                });
        completeness(record);
    }

    static Usage sumUsage(List<Usage> usages) {
        List<Usage> present = usages.stream().filter(Objects::nonNull).toList();
        Usage total = new Usage();
        total.setInputTokens(sumLong(present, Usage::getInputTokens));
        total.setCachedInputTokens(sumLong(present, Usage::getCachedInputTokens));
        total.setOutputTokens(sumLong(present, Usage::getOutputTokens));
        total.setReasoningTokens(sumLong(present, Usage::getReasoningTokens));
        total.setTotalTokens(sumLong(present, Usage::getTotalTokens));
        total.setImageCount(sumInt(present, Usage::getImageCount));
        total.setAudioDurationMs(sumLong(present, Usage::getAudioDurationMs));
        total.setInputChars(sumInt(present, Usage::getInputChars));
        total.setOutputChars(sumInt(present, Usage::getOutputChars));
        total.setOutputBytes(sumLong(present, Usage::getOutputBytes));
        total.setProviderDurationMs(sumLong(present, Usage::getProviderDurationMs));
        present.stream().filter(u -> u.getImageWidth() != null).reduce((a, b) -> b).ifPresent(u -> {
            total.setImageWidth(u.getImageWidth());
            total.setImageHeight(u.getImageHeight());
        });
        if (present.stream().anyMatch(u -> u.getUsageBasis() == UsageBasis.PROVIDER_REPORTED)) {
            total.setUsageBasis(UsageBasis.PROVIDER_REPORTED);
        } else if (present.stream().anyMatch(u -> u.getUsageBasis() == UsageBasis.DERIVED_LOCAL)) {
            total.setUsageBasis(UsageBasis.DERIVED_LOCAL);
        } else {
            total.setUsageBasis(UsageBasis.NOT_REPORTED);
        }
        return total;
    }

    static Cost sumCost(List<Cost> costs) {
        Cost total = new Cost();
        List<Cost> costed = new ArrayList<>();
        int uncosted = 0;
        for (Cost cost : costs) {
            if (cost == null || cost.getCostBasis() == null || cost.getCostBasis() == CostBasis.UNAVAILABLE) {
                uncosted++;
            } else {
                costed.add(cost);
            }
        }
        total.setCostedAttempts(costed.size());
        total.setUncostedAttempts(uncosted);
        total.setComplete(uncosted == 0 && !costed.isEmpty());
        if (costed.isEmpty()) {
            total.setCostBasis(CostBasis.UNAVAILABLE);
            total.setInfraCostBasis(CostBasis.UNAVAILABLE);
            total.setFormula(uncosted + " call(s), none could be priced");
            return total;
        }
        total.setInputCost(sumDouble(costed, Cost::getInputCost));
        total.setCachedInputCost(sumDouble(costed, Cost::getCachedInputCost));
        total.setOutputCost(sumDouble(costed, Cost::getOutputCost));
        total.setReasoningCost(sumDouble(costed, Cost::getReasoningCost));
        total.setModalityCost(sumDouble(costed, Cost::getModalityCost));
        total.setDirectCost(sumDouble(costed, Cost::getDirectCost));
        total.setInfraCost(sumDouble(costed, Cost::getInfraCost));
        total.setTotalCost(sumDouble(costed, Cost::getTotalCost));
        total.setCurrency(costed.get(0).getCurrency());
        total.setCostBasis(costed.stream().anyMatch(c -> c.getCostBasis() == CostBasis.ESTIMATED)
                ? CostBasis.ESTIMATED : CostBasis.CALCULATED);
        total.setInfraCostBasis(costed.stream().anyMatch(c -> c.getInfraCostBasis() == CostBasis.ESTIMATED)
                ? CostBasis.ESTIMATED : CostBasis.UNAVAILABLE);
        Set<String> versions = new LinkedHashSet<>();
        costed.forEach(c -> { if (c.getPricingVersion() != null) versions.add(c.getPricingVersion()); });
        total.setPricingVersion(versions.isEmpty() ? null : String.join(",", versions));
        Set<String> rateIds = new LinkedHashSet<>();
        costed.forEach(c -> { if (c.getPricingRateId() != null) rateIds.add(c.getPricingRateId()); });
        total.setPricingRateId(rateIds.isEmpty() ? null : String.join(",", rateIds));
        total.setCalculatedAt(costed.get(costed.size() - 1).getCalculatedAt());
        total.setFormula(costed.size() == 1 && uncosted == 0
                ? costed.get(0).getFormula()
                : "Sum of " + costed.size() + " priced call(s)" + (uncosted > 0 ? "; " + uncosted + " call(s) could not be priced" : ""));
        return total;
    }

    private static void completeness(AiRequestAnalytics record) {
        // Set by the reconciler for a request whose completion was never recorded; not derivable from attempts.
        boolean neverCompleted = record.getMissingFields() != null && record.getMissingFields().contains("completion");
        List<String> missing = new ArrayList<>();
        if (record.getUsageTotals() == null || record.getUsageTotals().getUsageBasis() == UsageBasis.NOT_REPORTED) {
            missing.add("usage");
        }
        if (record.getCostTotals() == null || !Boolean.TRUE.equals(record.getCostTotals().getComplete())) {
            missing.add("cost");
        }
        boolean providerCallExpected = record.getStatus() != AnalyticsStatus.REUSED && record.getStatus() != AnalyticsStatus.REJECTED;
        if (providerCallExpected && (record.getAttempts() == null || record.getAttempts().isEmpty())) {
            missing.add("attempts");
        }
        if (!providerCallExpected) {
            missing.clear();
        }
        if (neverCompleted) {
            missing.add("completion");
        }
        record.setMissingFields(missing);
        record.setDataCompleteness(missing.isEmpty() ? DataCompleteness.COMPLETE : DataCompleteness.PARTIAL);
    }

    private static <T> Long sumLong(List<T> items, Function<T, Long> field) {
        Long sum = null;
        for (T item : items) {
            Long value = field.apply(item);
            if (value != null) sum = (sum == null ? 0 : sum) + value;
        }
        return sum;
    }

    private static <T> Integer sumInt(List<T> items, Function<T, Integer> field) {
        Integer sum = null;
        for (T item : items) {
            Integer value = field.apply(item);
            if (value != null) sum = (sum == null ? 0 : sum) + value;
        }
        return sum;
    }

    private static <T> Double sumDouble(List<T> items, Function<T, Double> field) {
        Double sum = null;
        for (T item : items) {
            Double value = field.apply(item);
            if (value != null) sum = (sum == null ? 0 : sum) + value;
        }
        return sum;
    }
}
