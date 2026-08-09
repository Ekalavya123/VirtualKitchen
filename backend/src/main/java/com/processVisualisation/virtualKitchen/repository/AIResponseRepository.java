package com.processVisualisation.virtualKitchen.repository;

import com.processVisualisation.virtualKitchen.model.AIResponseDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AIResponseRepository extends MongoRepository<AIResponseDocument, String> {
    List<AIResponseDocument> findByContext(String context);
    List<AIResponseDocument> findByContextAndSuccess(String context, Boolean success);
}

