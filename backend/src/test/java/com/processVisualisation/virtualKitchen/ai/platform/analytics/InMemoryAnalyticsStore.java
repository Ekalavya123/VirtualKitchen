package com.processVisualisation.virtualKitchen.ai.platform.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@link AiAnalyticsStore} over a map, with the same compare-and-set semantics as the Mongo store. Records are
 * deep-copied on the way in and out, as a database would, so a caller can never mutate stored state in place.
 * {@link #failNextWrites} simulates an outage.
 */
public class InMemoryAnalyticsStore implements AiAnalyticsStore {

    private static final ObjectMapper COPIER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Map<String, AiRequestAnalytics> records = new ConcurrentHashMap<>();
    public final AtomicInteger failNextWrites = new AtomicInteger();

    @Override
    public synchronized boolean insertIfAbsent(AiRequestAnalytics record) {
        maybeFail();
        if (records.containsKey(record.getId())) {
            return false;
        }
        records.put(record.getId(), copy(record));
        return true;
    }

    @Override
    public Optional<AiRequestAnalytics> findById(String id) {
        AiRequestAnalytics record = records.get(id);
        return Optional.ofNullable(record == null ? null : copy(record));
    }

    @Override
    public synchronized boolean replaceIfRevision(AiRequestAnalytics replacement, long expectedRevision) {
        maybeFail();
        AiRequestAnalytics current = records.get(replacement.getId());
        if (current == null || current.getRevision() != expectedRevision) {
            return false;
        }
        records.put(replacement.getId(), copy(replacement));
        return true;
    }

    public AiRequestAnalytics get(String id) {
        return findById(id).orElseThrow();
    }

    public int size() {
        return records.size();
    }

    /** The id of the single stored record. */
    public String onlyId() {
        if (records.size() != 1) {
            throw new IllegalStateException("Expected exactly one record, found " + records.size());
        }
        return records.keySet().iterator().next();
    }

    private void maybeFail() {
        if (failNextWrites.get() > 0) {
            failNextWrites.decrementAndGet();
            throw new IllegalStateException("simulated store outage");
        }
    }

    private static AiRequestAnalytics copy(AiRequestAnalytics record) {
        try {
            return COPIER.readValue(COPIER.writeValueAsBytes(record), AiRequestAnalytics.class);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
