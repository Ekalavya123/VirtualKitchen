package com.processVisualisation.virtualKitchen.common.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the {@link MongoClient} bean used to connect to the
 * application's MongoDB instance. The connection URI is read from the
 * {@code spring.data.mongodb.uri} property, falling back to a hardcoded
 * default connection string when the property is not set.
 */
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:mongodb+srv://originSeed:originSeed123@originseed.fx177fa.mongodb.net/OriginSeed}")
    private String mongoUri;

    /**
     * Creates the {@link MongoClient} used by the application to connect to
     * MongoDB, using the URI resolved into {@link #mongoUri}.
     *
     * @return a {@link MongoClient} connected to the configured MongoDB instance
     */
    @Bean
    public MongoClient mongoClient() {
        System.out.println("========================================");
        System.out.println("Connecting to MongoDB with URI:");
        System.out.println(mongoUri);
        System.out.println("========================================");
        return MongoClients.create(mongoUri);
    }
}
