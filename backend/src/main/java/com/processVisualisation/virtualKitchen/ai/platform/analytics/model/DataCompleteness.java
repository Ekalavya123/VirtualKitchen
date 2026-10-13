package com.processVisualisation.virtualKitchen.ai.platform.analytics.model;

/** How complete a record is, so aggregates can say when they rest on partial data. */
public enum DataCompleteness {
    COMPLETE,
    PARTIAL,
    HISTORICAL_PARTIAL
}
