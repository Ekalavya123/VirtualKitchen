package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dto.AIResponseRecordDTO;
import com.processVisualisation.virtualKitchen.ai.model.AIResponseDocument;

import java.util.List;
import java.util.Optional;

public interface IAIResponseService {
    AIResponseDocument save(AIResponseRecordDTO dto);
    Optional<AIResponseDocument> findById(String id);
    List<AIResponseDocument> findAll();
    List<AIResponseDocument> findByContext(String context);
    List<AIResponseDocument> findByContextAndSuccess(String context, Boolean success);
    void deleteById(String id);
}

