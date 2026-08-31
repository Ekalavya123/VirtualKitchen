package com.processVisualisation.virtualKitchen.restclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {

    private String url;
    private String serviceKey;
    private String bucket;
    private Long timeoutMs;
}
