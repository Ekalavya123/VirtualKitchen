package com.processVisualisation.virtualKitchen.ai.platform.analytics.pricing;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code vk.ai.pricing.*}. Token prices themselves stay where they already are ({@code ai.models[n].*-usd-per-million});
 * this adds the rate-card version they are published under and the optional local-compute estimates.
 */
@Data
@Component
@ConfigurationProperties(prefix = "vk.ai.pricing")
public class AnalyticsPricingProperties {

    /**
     * Version label for the rates currently configured. Bump it whenever a price changes: the previous card is
     * closed and a new one opened, so historical costs keep the rates they were calculated with.
     */
    private String version = "v1";

    private String currency = "USD";

    /** Estimated USD per second of call duration for local engines, keyed by model key. Unset = unavailable. */
    private Map<String, Double> localInfraUsdPerSecond = new LinkedHashMap<>();

    /** Optional per-image price by model key, for providers that bill per image rather than per token. */
    private Map<String, Double> perImageUsd = new LinkedHashMap<>();

    /** Optional per-second price by model key, for providers that bill audio by duration rather than per token. */
    private Map<String, Double> perAudioSecondUsd = new LinkedHashMap<>();
}
