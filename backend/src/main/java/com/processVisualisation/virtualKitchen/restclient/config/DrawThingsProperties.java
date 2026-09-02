package com.processVisualisation.virtualKitchen.restclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai.drawthings")
public class DrawThingsProperties {

    private String baseUrl;
    private String endpoint;
    private Integer width ;
    private Integer height ;
    private Long timeoutMs;
}
