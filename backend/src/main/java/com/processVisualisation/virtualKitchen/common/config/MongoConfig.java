package com.processVisualisation.virtualKitchen.common.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the {@link MongoClient} bean used to connect to the
 * application's MongoDB instance. The connection URI is read from the
 * {@code spring.data.mongodb.uri} property, falling back to a local,
 * credential-free MongoDB when the property is not set. The URI is never
 * logged, since it normally carries the database username and password.
 */
@Configuration
public class MongoConfig {

    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/virtualKitchen}")
    private String mongoUri;

    /**
     * Creates the {@link MongoClient} used by the application to connect to
     * MongoDB, using the URI resolved into {@link #mongoUri}.
     *
     * @return a {@link MongoClient} connected to the configured MongoDB instance
     */
    @Bean
    public MongoClient mongoClient() {
        return MongoClients.create(mongoUri);
    }
}
