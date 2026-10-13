package com.processVisualisation.virtualKitchen.ai.platform.queue;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AiRequestJobRepository extends MongoRepository<AiRequestJob, String> {
    List<AiRequestJob> findByUserIdOrderByQueuedAtDesc(Long userId);
}
