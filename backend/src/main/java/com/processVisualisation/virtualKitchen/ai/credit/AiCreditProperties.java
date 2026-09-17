package com.processVisualisation.virtualKitchen.ai.credit;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.credits")
public class AiCreditProperties {

    /** Credits granted to a user's account at the start of each monthly cycle. */
    private int defaultMonthlyAllocation = 100;
}
