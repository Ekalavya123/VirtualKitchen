package com.processVisualisation.virtualKitchen.recipe.repository;

import com.processVisualisation.virtualKitchen.recipe.model.FlowDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface FlowRepository extends MongoRepository<FlowDocument, String> {
    Optional<FlowDocument> findByFlowId(String flowId);
    boolean existsByFlowId(String flowId);
}
