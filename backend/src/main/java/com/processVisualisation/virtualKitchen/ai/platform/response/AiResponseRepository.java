package com.processVisualisation.virtualKitchen.ai.platform.response;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link AiResponseDocument}, persisting
 * AI interaction records in the {@code ai_responses} collection.
 */
public interface AiResponseRepository extends MongoRepository<AiResponseDocument, String> {
    /** Finds all response records recorded under the given context. */
    List<AiResponseDocument> findByContext(String context);
    /** Finds all response records for the given context filtered by success flag. */
    List<AiResponseDocument> findByContextAndSuccess(String context, Boolean success);
}

