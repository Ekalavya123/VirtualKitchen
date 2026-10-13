package com.processVisualisation.virtualKitchen.ai.platform.analytics.query;

import org.springframework.data.mongodb.core.query.Criteria;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/** Translates an {@link AnalyticsFilter} into one MongoDB criteria; every filter is applied by the database. */
public final class AnalyticsCriteria {

    /** Sort keys accepted by the request list, mapped to document fields. */
    public static final Map<String, String> SORT_FIELDS = Map.of(
            "createdAt", "createdAt",
            "cost", "totalCost",
            "inputTokens", "usageTotals.inputTokens",
            "outputTokens", "usageTotals.outputTokens",
            "latency", "totalMs",
            "retries", "transportAttempts");

    private AnalyticsCriteria() {
    }

    public static Criteria build(AnalyticsFilter filter) {
        List<Criteria> parts = new ArrayList<>();
        if (filter.getFrom() != null || filter.getTo() != null) {
            Criteria created = where("createdAt");
            if (filter.getFrom() != null) created = created.gte(filter.getFrom());
            if (filter.getTo() != null) created = created.lt(filter.getTo());
            parts.add(created);
        }
        in(parts, "requestType", filter.getRequestType());
        in(parts, "operationContext", filter.getOperationContext());
        in(parts, "finalProvider", filter.getProvider());
        in(parts, "resolvedModelKey", filter.getModel());
        in(parts, "modelTier", filter.getTier());
        in(parts, "status", filter.getStatus());
        in(parts, "source", filter.getSource());
        eq(parts, "userId", filter.getUserId());
        eq(parts, "recipeId", blankToNull(filter.getRecipeId()));
        eq(parts, "workflowId", blankToNull(filter.getWorkflowId()));
        eq(parts, "jobId", blankToNull(filter.getJobId()));
        eq(parts, "stepId", blankToNull(filter.getStepId()));
        String requestId = blankToNull(filter.getRequestId());
        if (requestId != null) {
            parts.add(requestId.length() >= 6 && requestId.length() < 36
                    ? where("_id").regex("^" + Pattern.quote(requestId))
                    : where("_id").is(requestId));
        }
        if (filter.getCostMin() != null || filter.getCostMax() != null) {
            Criteria cost = where("totalCost");
            if (filter.getCostMin() != null) cost = cost.gte(filter.getCostMin());
            if (filter.getCostMax() != null) cost = cost.lte(filter.getCostMax());
            parts.add(cost);
        }
        if (filter.getLatencyMin() != null || filter.getLatencyMax() != null) {
            Criteria latency = where("totalMs");
            if (filter.getLatencyMin() != null) latency = latency.gte(filter.getLatencyMin());
            if (filter.getLatencyMax() != null) latency = latency.lte(filter.getLatencyMax());
            parts.add(latency);
        }
        if (filter.getRetryMin() != null && filter.getRetryMin() > 0) {
            parts.add(where("transportAttempts").gte(filter.getRetryMin() + 1));
        }
        return parts.isEmpty() ? new Criteria() : new Criteria().andOperator(parts.toArray(new Criteria[0]));
    }

    private static void in(List<Criteria> parts, String field, List<String> values) {
        if (values == null) return;
        List<String> clean = values.stream().filter(v -> v != null && !v.isBlank()).toList();
        if (!clean.isEmpty()) parts.add(where(field).in(clean));
    }

    private static void eq(List<Criteria> parts, String field, Object value) {
        if (value != null) parts.add(where(field).is(value));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
