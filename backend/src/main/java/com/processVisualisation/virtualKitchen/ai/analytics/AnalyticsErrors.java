package com.processVisualisation.virtualKitchen.ai.analytics;

import com.processVisualisation.virtualKitchen.ai.analytics.model.AnalyticsStatus;

import java.util.regex.Pattern;

/** Classifies AI failures into stable categories and sanitizes their messages before they are stored. */
public final class AnalyticsErrors {

    public static final int MAX_MESSAGE_CHARS = 500;

    private static final Pattern[] SECRETS = {
            Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("AIza[0-9A-Za-z_-]{20,}"),
            Pattern.compile("sk-[A-Za-z0-9_-]{10,}"),
            Pattern.compile("(?i)(api[_-]?key|key|token|secret|password|authorization)\\s*[=:]\\s*\\S+"),
            Pattern.compile("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{5,}"),
            Pattern.compile("mongodb(\\+srv)?://\\S+"),
    };
    private static final Pattern QUERY_STRING = Pattern.compile("(https?://[^\\s?\"']+)\\?[^\\s\"']*");

    private AnalyticsErrors() {
    }

    /** A category for an exception's simple class name (and HTTP status, when the provider returned one). */
    public static String category(String errorType, Integer httpStatus) {
        if (errorType == null) {
            return null;
        }
        return switch (errorType) {
            case "AIAuthenticationException" -> "AUTH";
            case "AICommunicationException" -> httpStatus != null && httpStatus == 429 ? "RATE_LIMITED" : "PROVIDER_ERROR";
            case "AITimeoutException", "TimeoutException", "AiRequestTimeoutException" -> "TIMEOUT";
            case "AIInvalidResponseException", "JsonProcessingException", "JsonParseException" -> "INVALID_RESPONSE";
            case "RecipeProcessAiException" -> "VALIDATION";
            case "AiQueueFullException" -> "QUEUE_FULL";
            case "NoAvailableModelException" -> "NO_MODEL";
            case "DuplicateKeyException" -> "DUPLICATE_SUBMISSION";
            default -> "INTERNAL";
        };
    }

    public static String category(Throwable error) {
        return error == null ? null : category(error.getClass().getSimpleName(), null);
    }

    /** Whether the queue would retry this category (it retries timeouts and communication failures only). */
    public static Boolean retryable(String category) {
        if (category == null) {
            return null;
        }
        return switch (category) {
            case "TIMEOUT", "PROVIDER_ERROR", "RATE_LIMITED" -> true;
            default -> false;
        };
    }

    /** The terminal request status a failure maps to. */
    public static AnalyticsStatus terminalStatus(Throwable error) {
        String category = category(error);
        if (category == null) {
            return AnalyticsStatus.FAILED;
        }
        return switch (category) {
            case "TIMEOUT" -> AnalyticsStatus.TIMED_OUT;
            case "QUEUE_FULL", "NO_MODEL", "DUPLICATE_SUBMISSION" -> AnalyticsStatus.REJECTED;
            default -> AnalyticsStatus.FAILED;
        };
    }

    /** Strips credentials, tokens and URL query strings, collapses whitespace, and truncates. */
    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String clean = QUERY_STRING.matcher(message).replaceAll("$1?[redacted]");
        for (Pattern secret : SECRETS) {
            clean = secret.matcher(clean).replaceAll("[redacted]");
        }
        clean = clean.replaceAll("\\s+", " ").trim();
        return clean.length() <= MAX_MESSAGE_CHARS ? clean : clean.substring(0, MAX_MESSAGE_CHARS) + "...";
    }
}
