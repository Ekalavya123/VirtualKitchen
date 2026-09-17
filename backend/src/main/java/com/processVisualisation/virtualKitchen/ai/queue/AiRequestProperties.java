package com.processVisualisation.virtualKitchen.ai.queue;

import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
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

    public long timeoutFor(AiCapability capability, long defaultValue) {
        Long configured = timeoutMs.get(capability.name());
        return configured != null ? configured : defaultValue;
    }
}
