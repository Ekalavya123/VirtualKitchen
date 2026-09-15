package com.processVisualisation.virtualKitchen.ai.queue;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Admission-control limits for the bounded AI pools. {@code aiTextMaxDepth}
 * bounds the new {@code "ai-text"} pool (recipe generation / chat — today
 * unbounded); {@code aiImageMaxDepth} bounds the existing {@code
 * "visualization"} pool the async visualization pipeline already uses.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.queue")
public class AiQueueProperties {
    private int aiTextMaxDepth = 20;
    private int aiImageMaxDepth = 30;
}
