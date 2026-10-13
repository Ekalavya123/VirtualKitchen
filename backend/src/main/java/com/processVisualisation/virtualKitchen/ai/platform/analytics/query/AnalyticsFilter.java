package com.processVisualisation.virtualKitchen.ai.platform.analytics.query;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;
import java.util.List;

/**
 * Filters shared by every analytics endpoint, bound from query parameters. List-valued fields match any of their
 * values; everything is combined with AND and applied in the database query.
 */
@Data
public class AnalyticsFilter {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant to;

    private List<String> requestType;
    private List<String> operationContext;
    private List<String> provider;
    /** Registry model keys. */
    private List<String> model;
    private List<String> tier;
    private List<String> status;
    /** LIVE, BACKFILL_REQUEST_JOB, BACKFILL_AI_RESPONSE. */
    private List<String> source;

    private Long userId;
    private String recipeId;
    private String workflowId;
    private String jobId;
    private String stepId;
    /** Exact request id, or a prefix of at least 6 characters. */
    private String requestId;

    private Double costMin;
    private Double costMax;
    private Long latencyMin;
    private Long latencyMax;
    /** Minimum number of retries (transport attempts beyond the first). */
    private Integer retryMin;
}
