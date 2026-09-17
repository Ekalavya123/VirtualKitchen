package com.processVisualisation.virtualKitchen.ai.credit;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiCreditTransactionRepository extends MongoRepository<AiCreditTransaction, String> {
    List<AiCreditTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}
