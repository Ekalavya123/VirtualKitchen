package com.processVisualisation.virtualKitchen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

/**
 * Startup diagnostic runner that verifies MongoDB connectivity on application boot.
 * Logs the connected database name and its collection names, then writes a throwaway
 * document to a {@code testCollection} to confirm write access. Intended for manual
 * environment verification, not production use.
 */
@Component
public class MongoVerifyRunner implements CommandLineRunner {

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * Runs the connectivity check: prints the database name and collection names, then
     * inserts a single verification document into {@code testCollection}.
     *
     * @param args command-line arguments passed by Spring Boot (unused)
     */
    @Override
    public void run(String... args) {

        System.out.println("Database = "
                + mongoTemplate.getDb().getName());

        System.out.println("Collections = "
                + mongoTemplate.getCollectionNames());

        mongoTemplate.save(
                new org.bson.Document("name", "verify"),
                "testCollection"
        );

        System.out.println("Document inserted");
    }
}