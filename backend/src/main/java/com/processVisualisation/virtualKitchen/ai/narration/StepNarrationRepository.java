package com.processVisualisation.virtualKitchen.ai.narration;

import com.processVisualisation.virtualKitchen.ai.narration.model.StepNarration;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StepNarrationRepository extends MongoRepository<StepNarration, String> {

    Optional<StepNarration> findByNarrationKey(String narrationKey);

    List<StepNarration> findByNarrationKeyIn(Collection<String> narrationKeys);
}
