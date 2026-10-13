package com.processVisualisation.virtualKitchen.ai.platform.analytics.backfill;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One execution of the historical analytics backfill and what it found. */
@Data
@Document(collection = "ai_analytics_backfill_runs")
public class BackfillRun {

    @Id
    private String id;
    private Instant startedAt;
    private Instant finishedAt;
    private Long triggeredBy;
    /** RUNNING, COMPLETED or FAILED. */
    private String status;

    private int scanned;
    /** New records whose usage and cost were both recovered. */
    private int recovered;
    /** New records missing usage and/or cost (shown as unavailable, never zero). */
    private int partiallyRecovered;
    /** Records that already had analytics (live, or an earlier backfill) and were left untouched. */
    private int alreadyPresent;
    /** Source records deliberately not imported, e.g. generation responses with no reliable link to a request. */
    private int skipped;
    private int failed;
    private List<String> notes = new ArrayList<>();
    private List<String> failures = new ArrayList<>();
}
