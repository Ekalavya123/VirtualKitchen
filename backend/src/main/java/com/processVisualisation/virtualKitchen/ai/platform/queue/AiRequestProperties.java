package com.processVisualisation.virtualKitchen.ai.platform.queue;

import com.processVisualisation.virtualKitchen.ai.platform.registry.AiCapability;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "ai.request")
public class AiRequestProperties {

    /** Per-capability job timeout in milliseconds, keyed by {@link AiCapability#name()}. */
    private Map<String, Long> timeoutMs = new HashMap<>();

    /** Retries attempted for a transient (timeout/communication) provider failure, on top of the first attempt. */
    private int maxRetries = 1;

    private long retryBackoffMs = 500;

    /**
     * Extra retries for a throttled request (429/503, {@code AiRateLimitedException}). These are
     * separate from {@link #maxRetries} because a per-minute limit needs seconds-to-a-minute of
     * waiting, not one short pause: a bulk narration of 16 steps otherwise loses every step past the
     * provider's requests-per-minute limit.
     */
    private int rateLimitMaxRetries = 4;

    /** First wait after a throttle when the provider gives no Retry-After; doubles on each retry. */
    private long rateLimitBackoffMs = 5_000;

    /** Upper bound on any single throttle wait, including one the provider asked for. */
    private long rateLimitMaxBackoffMs = 45_000;

    /** USD -> INR rate used only to show request cost in INR in the logs; costs are always stored in USD. */
    private double usdToInr = 88.0;

    public long timeoutFor(AiCapability capability, long defaultValue) {
        Long configured = timeoutMs.get(capability.name());
        return configured != null ? configured : defaultValue;
    }
}
