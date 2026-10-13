package com.processVisualisation.virtualKitchen.ai.platform.response;

import com.processVisualisation.virtualKitchen.ai.platform.response.dto.AiResponseRecordDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persisted AI response records, backed by
 * {@link AiResponseRepository}, providing straightforward CRUD/query
 * operations over persisted AI response records.
 */
@Service
public class AiResponseService {

    private final AiResponseRepository repository;

    public AiResponseService(AiResponseRepository repository) {
        this.repository = repository;
    }

    /**
     * Maps the given DTO to a new document and persists it.
     *
     * @param dto the response record to save
     * @return the saved document
     */
    public AiResponseDocument save(AiResponseRecordDTO dto) {
        AiResponseDocument doc = new AiResponseDocument();
        doc.setContext(dto.getContext());
        doc.setInput(dto.getInput());
        doc.setSuccess(dto.getSuccess());
        doc.setResponseData(dto.getResponseData());
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        return repository.save(doc);
    }

    /**
     * {@inheritDoc}
     */
    public Optional<AiResponseDocument> annotate(String id, String context, Boolean success, Map<String, Object> extraResponseData) {
        return repository.findById(id).map(doc -> {
            doc.setContext(context);
            doc.setSuccess(success);
            if (extraResponseData != null) {
                extraResponseData.forEach((key, value) -> {
                    if (value != null) doc.getResponseData().put(key, value);
                });
            }
            doc.setUpdatedAt(LocalDateTime.now());
            return repository.save(doc);
        });
    }

    /**
     * {@inheritDoc}
     */
    public Optional<AiResponseDocument> findById(String id) {
        return repository.findById(id);
    }

    /**
     * {@inheritDoc}
     */
    public List<AiResponseDocument> findAll() {
        return repository.findAll();
    }

    /**
     * {@inheritDoc}
     */
    public List<AiResponseDocument> findByContext(String context) {
        return repository.findByContext(context);
    }

    /**
     * {@inheritDoc}
     */
    public List<AiResponseDocument> findByContextAndSuccess(String context, Boolean success) {
        return repository.findByContextAndSuccess(context, success);
    }

    /**
     * {@inheritDoc}
     */
    public void deleteById(String id) {
        repository.deleteById(id);
    }
}

