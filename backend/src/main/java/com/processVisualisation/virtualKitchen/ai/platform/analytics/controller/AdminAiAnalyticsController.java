package com.processVisualisation.virtualKitchen.ai.platform.analytics.controller;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.AiAnalyticsRecorder;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.AnalyticsHealth;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill.AiAnalyticsBackfillService;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill.BackfillRun;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.pricing.PricingRateCard;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.pricing.PricingRegistry;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AiResultResolver;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AnalyticsFilter;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.query.AnalyticsQueryService;
import com.processVisualisation.virtualKitchen.auth.security.CurrentUser;
import com.processVisualisation.virtualKitchen.common.web.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Admin API for AI cost and efficiency analytics. Everything is read from persisted analytics records.
 * <p>
 * Admin-only twice over: the {@code /api/v1/admin/**} URL rule in {@code SecurityConfig} requires the ADMIN role,
 * and every handler calls {@link CurrentUser#requireAdmin()}. There is no user-facing analytics endpoint, so no
 * user can reach another user's prompts, outputs or artifacts through this API. No provider configuration or
 * secrets are returned (model snapshots carry only key, tier, provider name, model id and prices).
 */
@RestController
@RequestMapping("/api/v1/admin/ai-analytics")
public class AdminAiAnalyticsController {

    private final AnalyticsQueryService queryService;
    private final AiResultResolver resultResolver;
    private final AiAnalyticsBackfillService backfillService;
    private final PricingRegistry pricingRegistry;
    private final AiAnalyticsRecorder recorder;
    private final AnalyticsHealth health;

    public AdminAiAnalyticsController(AnalyticsQueryService queryService, AiResultResolver resultResolver,
                                      AiAnalyticsBackfillService backfillService, PricingRegistry pricingRegistry,
                                      AiAnalyticsRecorder recorder, AnalyticsHealth health) {
        this.queryService = queryService;
        this.resultResolver = resultResolver;
        this.backfillService = backfillService;
        this.pricingRegistry = pricingRegistry;
        this.recorder = recorder;
        this.health = health;
    }

    /** Filtered, sorted, paginated requests (attempt detail omitted; see {@code GET /requests/{id}}). */
    @GetMapping("/requests")
    public ApiResponse<AnalyticsQueryService.Page<AiRequestAnalytics>> requests(
            @ModelAttribute AnalyticsFilter filter,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        CurrentUser.requireAdmin();
        return ok(queryService.list(filter, sort, direction, page, size));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<ApiResponse<AnalyticsQueryService.RequestDetail>> request(@PathVariable String id) {
        CurrentUser.requireAdmin();
        return queryService.detail(id)
                .map(detail -> ResponseEntity.ok(ok(detail)))
                .orElseGet(() -> notFound("No analytics record " + id));
    }

    /**
     * The request's AI result with its efficiency metrics, fetched on demand from the existing stores.
     *
     * @param attemptId a specific provider call's output instead of the request's accepted result
     * @param full      return long text in full instead of truncated
     * @param prompt    also return the input prompt, for comparing it with the output
     */
    @GetMapping("/requests/{id}/result")
    public ResponseEntity<ApiResponse<AiResultResolver.Result>> result(
            @PathVariable String id,
            @RequestParam(required = false) String attemptId,
            @RequestParam(defaultValue = "false") boolean full,
            @RequestParam(defaultValue = "false") boolean prompt) {
        CurrentUser.requireAdmin();
        return resultResolver.resolve(id, attemptId, full, prompt)
                .map(result -> ResponseEntity.ok(ok(result)))
                .orElseGet(() -> notFound("No analytics record " + id));
    }

    /**
     * Grouped metrics; {@code groupBy} is a comma-separated list of dimensions (e.g. {@code operationContext,model}).
     * Empty or absent: only the totals over every matching request (one group).
     */
    @GetMapping("/aggregate")
    public ApiResponse<AnalyticsQueryService.AggregateResult> aggregate(
            @ModelAttribute AnalyticsFilter filter,
            @RequestParam(required = false) String groupBy) {
        CurrentUser.requireAdmin();
        List<String> dims = groupBy == null ? List.of() : Arrays.stream(groupBy.split(",")).map(String::trim).toList();
        return ok(queryService.aggregate(filter, dims));
    }

    @GetMapping("/overview")
    public ApiResponse<AnalyticsQueryService.Overview> overview(
            @ModelAttribute AnalyticsFilter filter,
            @RequestParam(required = false) String bucket) {
        CurrentUser.requireAdmin();
        return ok(queryService.overview(filter, bucket));
    }

    /** Model comparison within one operation context. */
    @GetMapping("/compare")
    public ApiResponse<AnalyticsQueryService.Comparison> compare(
            @ModelAttribute AnalyticsFilter filter,
            @RequestParam String operationContext) {
        CurrentUser.requireAdmin();
        return ok(queryService.compare(filter, operationContext));
    }

    /** Values for the filter controls, and the display currency with its recorded USD conversion rate. */
    @GetMapping("/options")
    public ApiResponse<AnalyticsQueryService.AnalyticsOptions> options() {
        CurrentUser.requireAdmin();
        return ok(queryService.filterOptions());
    }

    @GetMapping("/pricing")
    public ApiResponse<List<PricingRateCard>> pricing() {
        CurrentUser.requireAdmin();
        return ok(pricingRegistry.allCards());
    }

    @GetMapping("/backfill/runs")
    public ApiResponse<List<BackfillRun>> backfillRuns() {
        CurrentUser.requireAdmin();
        return ok(backfillService.recentRuns());
    }

    /** Runs the historical backfill. Safe to repeat: existing analytics records are never duplicated or changed. */
    @PostMapping("/backfill")
    public ResponseEntity<ApiResponse<BackfillRun>> backfill() {
        Long adminId = CurrentUser.requireAdmin();
        try {
            return ResponseEntity.ok(ok(backfillService.run(adminId)));
        } catch (IllegalStateException alreadyRunning) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<BackfillRun>builder()
                    .success(false).message(alreadyRunning.getMessage()).timestamp(LocalDateTime.now()).build());
        }
    }

    /** Analytics write failures and retries: a non-zero {@code dropped} means some analytics data was lost. */
    @GetMapping("/health")
    public ApiResponse<AnalyticsHealth.Snapshot> health() {
        CurrentUser.requireAdmin();
        return ok(health.snapshot(recorder.pendingWrites()));
    }

    private static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).timestamp(LocalDateTime.now()).build();
    }

    private static <T> ResponseEntity<ApiResponse<T>> notFound(String message) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.<T>builder()
                .success(false).message(message).timestamp(LocalDateTime.now()).build());
    }
}
