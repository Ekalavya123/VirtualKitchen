package com.processVisualisation.virtualKitchen.ai.platform.analytics;

import com.processVisualisation.virtualKitchen.ai.platform.analytics.model.AiRequestAnalytics;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Component
public class MongoAiAnalyticsStore implements AiAnalyticsStore {

    private final MongoTemplate mongoTemplate;

    public MongoAiAnalyticsStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public boolean insertIfAbsent(AiRequestAnalytics record) {
        try {
            mongoTemplate.insert(record);
            return true;
        } catch (DuplicateKeyException alreadyThere) {
            return false;
        }
    }

    @Override
    public Optional<AiRequestAnalytics> findById(String id) {
        return Optional.ofNullable(mongoTemplate.findById(id, AiRequestAnalytics.class));
    }

    @Override
    public boolean replaceIfRevision(AiRequestAnalytics replacement, long expectedRevision) {
        Query query = Query.query(where("_id").is(replacement.getId()).and("revision").is(expectedRevision));
        return mongoTemplate.findAndReplace(query, replacement) != null;
    }
}
