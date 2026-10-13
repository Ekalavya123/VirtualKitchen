package com.processVisualisation.virtualKitchen.ai.platform.analytics.model;

/** The modality of a request, independent of what the application used it for ({@link OperationContext}). */
public enum AnalyticsRequestType {
    TEXT_TO_TEXT,
    TEXT_TO_IMAGE,
    TEXT_TO_AUDIO,
    UNKNOWN
}
