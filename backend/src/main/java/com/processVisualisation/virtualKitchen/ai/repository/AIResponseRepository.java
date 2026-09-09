package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

/**
 * Spring Data MongoDB repository for {@link AIResponseDocument}, persisting
 * AI interaction records in the {@code ai_responses} collection.
 */
public interface AIResponseRepository extends MongoRepository<AIResponseDocument, String> {
    /** Finds all response records recorded under the given context. */
    List<AIResponseDocument> findByContext(String context);
    /** Finds all response records for the given context filtered by success flag. */
    List<AIResponseDocument> findByContextAndSuccess(String context, Boolean success);
}

