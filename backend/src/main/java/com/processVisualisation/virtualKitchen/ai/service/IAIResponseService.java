package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;

import java.util.List;
import java.util.Optional;

/**
 * Service contract for persisting and querying records of AI interactions
 * ({@link AIResponseDocument}), used for analytics/training purposes and to
 * back the AI response CRUD endpoints.
 */
public interface IAIResponseService {

    /**
     * Persists a new AI response record.
     *
     * @param dto the response record to save
     * @return the saved document, including its generated identifier
     */
    AIResponseDocument save(AIResponseRecordDTO dto);

    /**
     * Finds an AI response record by its identifier.
     *
     * @param id the document identifier
     * @return the matching document, or empty if none exists
     */
    Optional<AIResponseDocument> findById(String id);

    /**
     * Retrieves all persisted AI response records.
     *
     * @return the list of all response documents
     */
    List<AIResponseDocument> findAll();

    /**
     * Retrieves all AI response records recorded under the given context.
     *
     * @param context the logical context to filter by (e.g. "chat")
     * @return the matching response documents
     */
    List<AIResponseDocument> findByContext(String context);

    /**
     * Retrieves AI response records matching the given context and success flag.
     *
     * @param context the logical context to filter by
     * @param success the success flag to filter by
     * @return the matching response documents
     */
    List<AIResponseDocument> findByContextAndSuccess(String context, Boolean success);

    /**
     * Deletes an AI response record by its identifier.
     *
     * @param id the document identifier to delete
     */
    void deleteById(String id);
}

