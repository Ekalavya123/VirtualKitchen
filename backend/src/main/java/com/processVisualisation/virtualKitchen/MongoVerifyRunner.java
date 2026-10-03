package com.processVisualisation.virtualKitchen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MongoVerifyRunner.class);

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

        log.info("event=mongo_connection_verified database={} collections={}",
                mongoTemplate.getDb().getName(), mongoTemplate.getCollectionNames().size());

        mongoTemplate.save(
                new org.bson.Document("name", "verify"),
                "testCollection"
        );

        log.debug("event=mongo_write_verified collection=testCollection");
    }
}