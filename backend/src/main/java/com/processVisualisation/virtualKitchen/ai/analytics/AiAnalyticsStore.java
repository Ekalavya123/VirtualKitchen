package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.model.AiRequestAnalytics;

import java.util.Optional;

/** Persistence for {@link AiRequestAnalytics}, reduced to what the recorder needs: insert, read, compare-and-set. */
public interface AiAnalyticsStore {

    /** Inserts a new record; returns false (and changes nothing) when a record with that id already exists. */
    boolean insertIfAbsent(AiRequestAnalytics record);

    Optional<AiRequestAnalytics> findById(String id);

    /**
     * Replaces the stored record only if its revision is still {@code expectedRevision}; the replacement must
     * already carry {@code expectedRevision + 1}. Returns false when another writer got there first.
     */
    boolean replaceIfRevision(AiRequestAnalytics replacement, long expectedRevision);
}
