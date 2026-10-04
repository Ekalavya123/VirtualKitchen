package com.processVisualisation.virtualKitchen.restclient.client.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.processVisualisation.virtualKitchen.restclient.client.ProviderUsage;

/** Reads the {@code usageMetadata} block every Gemini {@code generateContent} response carries. */
final class GeminiUsage {

    private GeminiUsage() {
    }

    static ProviderUsage from(JsonNode root) {
        JsonNode usage = root == null ? null : root.path("usageMetadata");
        if (usage == null || usage.isMissingNode() || !usage.isObject()) {
            return ProviderUsage.NONE;
        }
        return new ProviderUsage(
                count(usage, "promptTokenCount"),
                count(usage, "candidatesTokenCount"),
                count(usage, "thoughtsTokenCount"),
                count(usage, "totalTokenCount"));
    }

    private static Long count(JsonNode usage, String field) {
        JsonNode value = usage.path(field);
        return value.isNumber() ? value.asLong() : null;
    }
}
