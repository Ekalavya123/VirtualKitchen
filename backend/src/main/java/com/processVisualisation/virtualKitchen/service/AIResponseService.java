package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.model.AIResponseDocument;
import com.processVisualisation.virtualKitchen.repository.AIResponseRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AIResponseService implements IAIResponseService {

    private final AIResponseRepository repository;

    public AIResponseService(AIResponseRepository repository) {
        this.repository = repository;
    }

    @Override
    public AIResponseDocument save(AIResponseRecordDTO dto) {
        AIResponseDocument doc = new AIResponseDocument();
        doc.setContext(dto.getContext());
        doc.setInput(dto.getInput());
        doc.setSuccess(dto.getSuccess());
        doc.setResponseData(dto.getResponseData());
        return repository.save(doc);
    }

    @Override
    public Optional<AIResponseDocument> findById(String id) {
        return repository.findById(id);
    }

    @Override
    public List<AIResponseDocument> findAll() {
        return repository.findAll();
    }

    @Override
    public List<AIResponseDocument> findByContext(String context) {
        return repository.findByContext(context);
    }

    @Override
    public List<AIResponseDocument> findByContextAndSuccess(String context, Boolean success) {
        return repository.findByContextAndSuccess(context, success);
    }

    @Override
    public void deleteById(String id) {
        repository.deleteById(id);
    }
}

