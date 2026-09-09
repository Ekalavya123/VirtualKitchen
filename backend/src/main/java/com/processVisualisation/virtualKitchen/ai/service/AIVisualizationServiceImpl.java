package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.ai.dto.VisualizationClipResponseDTO;
import com.processVisualisation.virtualKitchen.ai.dto.VisualizationResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.ai.model.VisualizationClip;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import com.processVisualisation.virtualKitchen.ai.repository.AIVisualizationClipRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default {@link AIVisualizationService} implementation that derives a
 * sequence of {@link VisualizationClip} placeholders from a recipe flow's
 * {@code recipeStepNode} nodes, creating or reusing one clip per step and
 * linking each to its predecessor for continuity.
 */
@Service
public class AIVisualizationServiceImpl implements AIVisualizationService {

    private final AIVisualizationClipRepository clipRepository;
    private final RecipeRepository recipeRepository;

    public AIVisualizationServiceImpl(AIVisualizationClipRepository clipRepository, RecipeRepository recipeRepository) {
        this.clipRepository = clipRepository;
        this.recipeRepository = recipeRepository;
    }

    /**
     * Builds (or updates) the visualization clips for a recipe flow's steps,
     * creating a new clip per {@code recipeStepNode} node when none exists
     * yet and preserving previously generated media/status fields otherwise.
     *
     * @param flowId identifier of the recipe flow to visualize
     * @return the visualization response with the ordered clips and final clip,
     *         or a message-only response if the flow or its template id is not found
     */
    @Override
    public VisualizationResponseDTO generateVisualization(String flowId) {
        // Fetch flow document from database using flowId
        Optional<Recipe> flowOpt = recipeRepository.findByFlowId(flowId);
        if (flowOpt.isEmpty()) {
            return VisualizationResponseDTO.builder()
                    .message("Flow not found for flowId: " + flowId)
                    .clips(new ArrayList<>())
                    .build();
        }

        Recipe flow = flowOpt.get();
        Long processTemplateId = flow.getTemplateId();
        
        if (processTemplateId == null) {
            return VisualizationResponseDTO.builder()
                    .message("Template ID not found in flow for flowId: " + flowId)
                    .clips(new ArrayList<>())
                    .build();
        }

        List<VisualizationClip> existingClips = clipRepository.findByProcessTemplateIdOrderByStepOrderAsc(processTemplateId);
        List<VisualizationClipResponseDTO> clips = new ArrayList<>();
        VisualizationClip previousClip = null;

        int stepOrder = 0;
        for (Recipe.NodeDocument node : flow.getNodes()) {
            String type = node.getType();
            if (!"recipeStepNode".equals(type)) {
                continue;
            }

            stepOrder++;
            Map<String, Object> data = node.getData() != null ? node.getData() : Map.of();
            String title = String.valueOf(data.getOrDefault("title", "Step " + stepOrder));
            String description = String.valueOf(data.getOrDefault("description", ""));
            String nodeId = node.getId();

            // Check if clip already exists for this node
            VisualizationClip clip = existingClips.stream()
                    .filter(candidate -> Objects.equals(candidate.getSourceNodeId(), nodeId))
                    .findFirst()
                    .orElseGet(VisualizationClip::new);

            clip.setProcessTemplateId(processTemplateId);
            clip.setParentClipId(previousClip == null ? null : previousClip.getClipId());
            clip.setClipId("clip-" + processTemplateId + "-" + stepOrder);
            clip.setTitle(title);
            clip.setDescription(description);
            clip.setStepOrder(stepOrder);
            clip.setMediaUrl(clip.getMediaUrl() == null ? "/clips/" + clip.getClipId() + ".mp4" : clip.getMediaUrl());
            clip.setThumbnailUrl(clip.getThumbnailUrl() == null ? "/thumbnails/" + clip.getClipId() + ".jpg" : clip.getThumbnailUrl());
            clip.setStatus(clip.getStatus() == null ? "pending" : clip.getStatus());
            clip.setCameraAngle(clip.getCameraAngle() == null ? "front" : clip.getCameraAngle());
            clip.setSourceNodeId(nodeId);
            clipRepository.save(clip);

            previousClip = clip;
            clips.add(toResponseDTO(clip));
        }

        VisualizationClipResponseDTO finalClip = clips.isEmpty() ? null : clips.get(clips.size() - 1);
        return VisualizationResponseDTO.builder()
                .processTemplateId(processTemplateId)
                .message("Visualization generated")
                .clips(clips)
                .finalClip(finalClip)
                .build();
    }

    private VisualizationClipResponseDTO toResponseDTO(VisualizationClip clip) {
        return VisualizationClipResponseDTO.builder()
                .id(clip.getId())
                .clipId(clip.getClipId())
                .parentClipId(clip.getParentClipId())
                .title(clip.getTitle())
                .description(clip.getDescription())
                .stepOrder(clip.getStepOrder())
                .mediaUrl(clip.getMediaUrl())
                .thumbnailUrl(clip.getThumbnailUrl())
                .status(clip.getStatus())
                .cameraAngle(clip.getCameraAngle())
                .sourceNodeId(clip.getSourceNodeId())
                .build();
    }
}
