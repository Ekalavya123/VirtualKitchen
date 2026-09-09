package com.processVisualisation.virtualKitchen.ai.repository;

import com.processVisualisation.virtualKitchen.ai.model.VisualizationClip;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data MongoDB repository for {@link VisualizationClip}, persisting
 * individual generated visualization clips in the
 * {@code visualization_clip} collection.
 */
public interface AIVisualizationClipRepository extends MongoRepository<VisualizationClip, Long> {
    /** Finds all clips for a process template, ordered by their step sequence. */
    List<VisualizationClip> findByProcessTemplateIdOrderByStepOrderAsc(Long processTemplateId);
    /** Finds the clip for a process template at a specific step order, if present. */
    Optional<VisualizationClip> findByProcessTemplateIdAndStepOrder(Long processTemplateId, int stepOrder);
}
