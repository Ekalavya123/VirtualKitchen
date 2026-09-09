package com.processVisualisation.virtualKitchen.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application's shared Jackson {@link ObjectMapper} bean. No
 * custom modules or features are registered here — this simply exposes
 * Jackson's default configuration as a Spring-managed singleton so it can be
 * injected wherever JSON (de)serialization is needed.
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates the shared {@link ObjectMapper} bean used for JSON
     * serialization and deserialization throughout the application.
     *
     * @return a new {@link ObjectMapper} configured with Jackson defaults
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}