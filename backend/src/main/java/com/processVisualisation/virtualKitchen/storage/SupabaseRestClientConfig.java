package com.processVisualisation.virtualKitchen.storage;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class SupabaseRestClientConfig {

    @Bean
    @Qualifier("supabaseRestClient")
    public RestClient supabaseRestClient(
            SupabaseProperties properties) {

        return RestClient.builder()
                .baseUrl(properties.getUrl())
                .build();
    }
}
