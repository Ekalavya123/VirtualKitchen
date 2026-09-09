package com.processVisualisation.virtualKitchen.restclient.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds configuration properties under the {@code supabase} prefix,
 * including the project URL, service key, storage bucket name, and request
 * timeout used to call Supabase Storage. Getters and setters are generated
 * by Lombok's {@link Data} annotation.
 */
@Data
@Component
@ConfigurationProperties(prefix = "supabase")
public class SupabaseProperties {

    private String url;
    private String serviceKey;
    private String bucket;
    private Long timeoutMs;
}
