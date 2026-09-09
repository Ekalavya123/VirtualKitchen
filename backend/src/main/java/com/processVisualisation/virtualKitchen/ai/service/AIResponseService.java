package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;
import com.processVisualisation.virtualKitchen.ai.repository.AIResponseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Default {@link IAIResponseService} implementation backed by
 * {@link AIResponseRepository}, providing straightforward CRUD/query
 * operations over persisted AI response records.
 */
@Service
public class AIResponseService implements IAIResponseService {

    private final AIResponseRepository repository;

    public AIResponseService(AIResponseRepository repository) {
        this.repository = repository;
    }

    /**
     * Maps the given DTO to a new document and persists it.
     *
     * @param dto the response record to save
     * @return the saved document
     */
    @Override
    public AIResponseDocument save(AIResponseRecordDTO dto) {
        AIResponseDocument doc = new AIResponseDocument();
        doc.setContext(dto.getContext());
        doc.setInput(dto.getInput());
        doc.setSuccess(dto.getSuccess());
        doc.setResponseData(dto.getResponseData());
        return repository.save(doc);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<AIResponseDocument> findById(String id) {
        return repository.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AIResponseDocument> findAll() {
        return repository.findAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AIResponseDocument> findByContext(String context) {
        return repository.findByContext(context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<AIResponseDocument> findByContextAndSuccess(String context, Boolean success) {
        return repository.findByContextAndSuccess(context, success);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }
}

