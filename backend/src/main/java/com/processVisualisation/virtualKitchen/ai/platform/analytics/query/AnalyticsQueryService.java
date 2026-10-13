package com.processVisualisation.virtualKitchen.ai.platform.analytics.query;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsRequestType;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsSource;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AnalyticsStatus;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.OperationContext;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.pricing.PricingRateCard;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AnalyticsMath.GroupMetrics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AnalyticsMath.Row;
import com.processVisualisation.virtualKitchen.ai.platform.dispatch.LoggingAiClient;
import com.processVisualisation.virtualKitchen.ai.platform.queue.AiRequestProperties;
import com.processVisualisation.virtualKitchen.ai.platform.registry.AiModelProperties;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelDefinition;
import com.processVisualisation.virtualKitchen.ai.platform.registry.ModelTier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Read side of AI analytics: the filtered request list, request detail, and grouped metrics.
 * <p>
 * Filtering always happens in MongoDB ({@link AnalyticsCriteria}). Grouped metrics stream only the matching
 * records' metric fields from the database and reduce them server-side with {@link AnalyticsMath}, so medians and
 * percentiles are exact; nothing is computed in the browser. At most {@value #MAX_ROWS} records are reduced per
 * request — beyond that the response says it was truncated.
 */
@Service
public class AnalyticsQueryService {

    static final int MAX_ROWS = 200_000;
    static final int MAX_PAGE_SIZE = 100;

    private static final String[] METRIC_FIELDS = {
            "_id", "status", "attemptCount", "transportAttempts", "costTotals", "usageTotals", "totalMs", "queueWaitMs",
            "validationPassed", "dataCompleteness", "requestType", "operationContext", "finalProvider",
            "resolvedModelKey", "modelTier", "workflowId", "recipeId", "stepId", "userId", "source", "createdAt"};

    private static final String[] LIST_EXCLUDED_FIELDS = {"attempts", "modelSnapshot", "statusHistory"};

    /** Grouping dimensions accepted by {@link #aggregate}. */
    static final Map<String, Function<AiRequestAnalytics, String>> DIMENSIONS = dimensions();

    private final MongoTemplate mongoTemplate;
    private final AiModelProperties modelProperties;
    private final AiRequestProperties requestProperties;

    public AnalyticsQueryService(MongoTemplate mongoTemplate, AiModelProperties modelProperties,
                                 AiRequestProperties requestProperties) {
        this.mongoTemplate = mongoTemplate;
        this.modelProperties = modelProperties;
        this.requestProperties = requestProperties;
    }

    // --- request explorer ---------------------------------------------------------------------------------------

    public record Page<T>(List<T> items, long total, int page, int size) {
    }

    public Page<AiRequestAnalytics> list(AnalyticsFilter filter, String sort, String direction, int page, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int pageIndex = Math.max(0, page);
        String sortField = AnalyticsCriteria.SORT_FIELDS.getOrDefault(sort == null ? "createdAt" : sort, "createdAt");
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

        Query count = Query.query(AnalyticsCriteria.build(filter));
        long total = mongoTemplate.count(count, AiRequestAnalytics.class);

        Query query = Query.query(AnalyticsCriteria.build(filter))
                .with(Sort.by(sortDirection, sortField).and(Sort.by(Sort.Direction.DESC, "_id")))
                .skip((long) pageIndex * pageSize)
                .limit(pageSize);
        query.fields().exclude(LIST_EXCLUDED_FIELDS);
        return new Page<>(mongoTemplate.find(query, AiRequestAnalytics.class), total, pageIndex, pageSize);
    }

    public record RequestDetail(AiRequestAnalytics request, List<PricingRateCard> pricing) {
    }

    public Optional<RequestDetail> detail(String id) {
        AiRequestAnalytics record = mongoTemplate.findById(id, AiRequestAnalytics.class);
        if (record == null) {
            return Optional.empty();
        }
        Set<String> rateIds = new LinkedHashSet<>();
        record.getAttempts().forEach(a -> {
            if (a.getCost() != null && a.getCost().getPricingRateId() != null) rateIds.add(a.getCost().getPricingRateId());
        });
        List<PricingRateCard> cards = rateIds.isEmpty() ? List.of()
                : mongoTemplate.find(Query.query(where("_id").in(rateIds)), PricingRateCard.class);
        return Optional.of(new RequestDetail(record, cards));
    }

    // --- aggregates --------------------------------------------------------------------------------------------

    public record AggregateResult(List<String> groupBy, List<GroupMetrics> groups, GroupMetrics totals,
                                  int rowsScanned, boolean truncated) {
    }

    /** Groups the filtered requests by {@code groupBy} (see {@link #DIMENSIONS}) and summarizes each group. */
    public AggregateResult aggregate(AnalyticsFilter filter, List<String> groupBy) {
        List<String> dims = groupBy == null ? List.of()
                : groupBy.stream().filter(DIMENSIONS::containsKey).distinct().toList();
        RowSet rowSet = rows(filter, dims);
        List<GroupMetrics> groups = new ArrayList<>(AnalyticsMath.group(rowSet.rows()));
        boolean timeOnly = dims.size() == 1 && isTimeBucket(dims.get(0));
        groups.sort(timeOnly
                ? Comparator.comparing(g -> g.key().get(dims.get(0)), Comparator.nullsLast(Comparator.naturalOrder()))
                : Comparator.comparing(GroupMetrics::totalCost, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GroupMetrics::requestCount, Comparator.reverseOrder()));
        GroupMetrics totals = AnalyticsMath.summarize(Map.of(), rowSet.rows());
        return new AggregateResult(dims, groups, totals, rowSet.rows().size(), rowSet.truncated());
    }

    public record Overview(GroupMetrics totals, List<GroupMetrics> byModel, List<GroupMetrics> byContext,
                           List<GroupMetrics> byModality, List<GroupMetrics> overTime, String bucket,
                           List<GroupMetrics> topWorkflows, List<AiRequestAnalytics> topRequests,
                           int rowsScanned, boolean truncated) {
    }

    public Overview overview(AnalyticsFilter filter, String bucket) {
        String timeBucket = isTimeBucket(bucket) ? bucket : autoBucket(filter);
        RowSet rowSet = rows(filter, List.of("model", "operationContext", "requestType", timeBucket, "workflow"));
        List<Row> rows = rowSet.rows();
        List<GroupMetrics> byModel = regroup(rows, "model");
        List<GroupMetrics> byContext = regroup(rows, "operationContext");
        List<GroupMetrics> byModality = regroup(rows, "requestType");
        List<GroupMetrics> overTime = regroup(rows, timeBucket);
        overTime.sort(Comparator.comparing(g -> g.key().get(timeBucket), Comparator.nullsLast(Comparator.naturalOrder())));
        List<GroupMetrics> topWorkflows = regroup(rows.stream().filter(r -> r.key().get("workflow") != null).toList(), "workflow")
                .stream().limit(10).toList();

        Query top = Query.query(AnalyticsCriteria.build(filter)).addCriteria(where("totalCost").ne(null))
                .with(Sort.by(Sort.Direction.DESC, "totalCost")).limit(10);
        top.fields().exclude(LIST_EXCLUDED_FIELDS);
        List<AiRequestAnalytics> topRequests = mongoTemplate.find(top, AiRequestAnalytics.class);

        return new Overview(AnalyticsMath.summarize(Map.of(), rows), byModel, byContext, byModality, overTime,
                timeBucket, topWorkflows, topRequests, rows.size(), rowSet.truncated());
    }

    public record Comparison(String operationContext, List<GroupMetrics> models, int rowsScanned, boolean truncated) {
    }

    /**
     * Per-model metrics for one operation context, so only models that performed the same task are compared.
     * Grouped by model and tier, so a local engine's direct cost and estimated compute cost stay separate.
     */
    public Comparison compare(AnalyticsFilter filter, String operationContext) {
        filter.setOperationContext(List.of(operationContext));
        RowSet rowSet = rows(filter, List.of("model", "provider", "tier"));
        List<GroupMetrics> models = new ArrayList<>(AnalyticsMath.group(rowSet.rows()));
        models.sort(Comparator.comparing(GroupMetrics::requestCount, Comparator.reverseOrder()));
        return new Comparison(operationContext, models, rowSet.rows().size(), rowSet.truncated());
    }

    // --- internals ---------------------------------------------------------------------------------------------

    private record RowSet(List<Row> rows, boolean truncated) {
    }

    private RowSet rows(AnalyticsFilter filter, List<String> dims) {
        Query query = Query.query(AnalyticsCriteria.build(filter)).limit(MAX_ROWS + 1);
        query.fields().include(METRIC_FIELDS);
        List<Row> rows = new ArrayList<>();
        boolean truncated = false;
        try (Stream<AiRequestAnalytics> stream = mongoTemplate.stream(query, AiRequestAnalytics.class)) {
            var iterator = stream.iterator();
            while (iterator.hasNext()) {
                AiRequestAnalytics record = iterator.next();
                if (rows.size() == MAX_ROWS) {
                    truncated = true;
                    break;
                }
                rows.add(Row.of(record, keyOf(record, dims)));
            }
        }
        return new RowSet(rows, truncated);
    }

    /** Re-keys already loaded rows by one dimension (values were captured when the rows were loaded). */
    private static List<GroupMetrics> regroup(List<Row> rows, String dim) {
        List<Row> rekeyed = rows.stream().map(r -> new Row(singleKey(dim, r.key().get(dim)), r.status(), r.attemptCount(),
                r.transportAttempts(), r.totalCost(), r.costBasis(), r.directCost(), r.infraCost(), r.currency(),
                r.inputTokens(), r.outputTokens(), r.totalMs(), r.queueWaitMs(), r.validationPassed(), r.historical())).toList();
        List<GroupMetrics> groups = new ArrayList<>(AnalyticsMath.group(rekeyed));
        groups.sort(Comparator.comparing(GroupMetrics::totalCost, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(GroupMetrics::requestCount, Comparator.reverseOrder()));
        return groups;
    }

    private static Map<String, String> singleKey(String dim, String value) {
        Map<String, String> key = new LinkedHashMap<>();
        key.put(dim, value);
        return key;
    }

    static Map<String, String> keyOf(AiRequestAnalytics record, List<String> dims) {
        Map<String, String> key = new LinkedHashMap<>();
        for (String dim : dims) {
            key.put(dim, DIMENSIONS.get(dim).apply(record));
        }
        return key;
    }

    static boolean isTimeBucket(String dim) {
        return "day".equals(dim) || "week".equals(dim) || "month".equals(dim);
    }

    static String autoBucket(AnalyticsFilter filter) {
        if (filter.getFrom() == null) {
            return "week";
        }
        Instant to = filter.getTo() != null ? filter.getTo() : Instant.now();
        long days = Duration.between(filter.getFrom(), to).toDays();
        return days <= 62 ? "day" : days <= 400 ? "week" : "month";
    }

    private static Map<String, Function<AiRequestAnalytics, String>> dimensions() {
        Map<String, Function<AiRequestAnalytics, String>> dims = new LinkedHashMap<>();
        dims.put("requestType", r -> name(r.getRequestType()));
        dims.put("operationContext", r -> name(r.getOperationContext()));
        dims.put("provider", AiRequestAnalytics::getFinalProvider);
        dims.put("model", AiRequestAnalytics::getResolvedModelKey);
        dims.put("tier", AiRequestAnalytics::getModelTier);
        dims.put("status", r -> name(r.getStatus()));
        dims.put("source", r -> name(r.getSource()));
        dims.put("workflow", AiRequestAnalytics::getWorkflowId);
        dims.put("recipe", AiRequestAnalytics::getRecipeId);
        dims.put("step", AiRequestAnalytics::getStepId);
        dims.put("user", r -> r.getUserId() == null ? null : r.getUserId().toString());
        dims.put("day", r -> date(r) == null ? null : date(r).toString());
        dims.put("week", r -> date(r) == null ? null
                : String.format("%d-W%02d", date(r).get(IsoFields.WEEK_BASED_YEAR), date(r).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)));
        dims.put("month", r -> date(r) == null ? null : date(r).toString().substring(0, 7));
        return dims;
    }

    private static LocalDate date(AiRequestAnalytics record) {
        return record.getCreatedAt() == null ? null : LocalDate.ofInstant(record.getCreatedAt(), ZoneOffset.UTC);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * What the filter controls can offer, and how costs are displayed.
     *
     * @param values         per filter, every value it can take
     * @param displayCurrency the currency the admin UI shows costs in
     * @param usdToDisplay   the recorded conversion from stored USD costs ({@code ai.request.usd-to-inr})
     */
    public record AnalyticsOptions(Map<String, List<String>> values, String displayCurrency, double usdToDisplay) {
    }

    /**
     * Every value each filter can take: the full set of operations, types, statuses and sources, plus every model and
     * provider in the model registry, merged with any other values already present in the data (e.g. a model since
     * removed from configuration). So the filters are usable before any request has been recorded.
     */
    public AnalyticsOptions filterOptions() {
        Map<String, List<String>> values = new LinkedHashMap<>();
        values.put("operationContext", merge(enumNames(OperationContext.values(), OperationContext.UNKNOWN), distinct("operationContext")));
        values.put("requestType", merge(enumNames(AnalyticsRequestType.values(), AnalyticsRequestType.UNKNOWN), distinct("requestType")));
        values.put("model", merge(modelProperties.getModels().stream().map(ModelDefinition::getKey).toList(),
                distinct("resolvedModelKey")));
        values.put("provider", merge(modelProperties.getModels().stream()
                .map(m -> LoggingAiClient.providerOf(m.getProviderBean())).toList(), distinct("finalProvider")));
        values.put("tier", merge(enumNames(ModelTier.values(), null), distinct("modelTier")));
        values.put("status", merge(enumNames(AnalyticsStatus.values(), null), distinct("status")));
        values.put("source", merge(enumNames(AnalyticsSource.values(), null), distinct("source")));
        values.put("groupBy", new ArrayList<>(DIMENSIONS.keySet()));
        return new AnalyticsOptions(values, "INR", requestProperties.getUsdToInr());
    }

    private static List<String> enumNames(Enum<?>[] values, Enum<?> except) {
        return Arrays.stream(values).filter(v -> v != except).map(Enum::name).toList();
    }

    private static List<String> merge(List<String> known, List<String> present) {
        Set<String> all = new LinkedHashSet<>(known);
        all.addAll(present);
        all.remove(null);
        return new ArrayList<>(all);
    }

    private List<String> distinct(String field) {
        // Nulls are excluded in the query: findDistinct cannot map a null value (unset provider, model, ...).
        Query notNull = Query.query(where(field).ne(null));
        return mongoTemplate.findDistinct(notNull, field, AiRequestAnalytics.class, Object.class).stream()
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .sorted()
                .toList();
    }
}
