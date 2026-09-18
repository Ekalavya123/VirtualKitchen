package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;
import com.processVisualisation.virtualKitchen.common.exception.ProcessValidationException;
import com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessMapper;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import com.processVisualisation.virtualKitchen.recipe.validation.ProcessValidator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Default {@link IProcessService} implementation, backed by MongoDB via
 * {@link ProcessRepository}. Every operation is scoped to the recipe named
 * in its {@code recipeId} parameter: a process that exists but belongs to a
 * different recipe is treated as not found ({@link #requireBelongsToRecipe}),
 * matching the "recipe-scoped process references only" Phase 1 rule that
 * {@link ProcessValidator} also enforces at the graph level.
 */
@Service
public class ProcessServiceImpl implements IProcessService {

    private static final String COPY_SUFFIX = " (copy)";

    private final ProcessRepository processRepository;
    private final RecipeTemplateRepository recipeTemplateRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final ProcessValidator processValidator;
    private final ProcessMapper processMapper;

    public ProcessServiceImpl(
            ProcessRepository processRepository,
            RecipeTemplateRepository recipeTemplateRepository,
            SequenceGeneratorService sequenceGeneratorService,
            ProcessValidator processValidator,
            ProcessMapper processMapper
    ) {
        this.processRepository = processRepository;
        this.recipeTemplateRepository = recipeTemplateRepository;
        this.sequenceGeneratorService = sequenceGeneratorService;
        this.processValidator = processValidator;
        this.processMapper = processMapper;
    }

    @Override
    public ProcessResponseDTO create(Long recipeId, Long userId, ProcessRequestDTO dto) {
        requireOwnedRecipe(recipeId, userId);

        Process process = new Process();
        process.setId(sequenceGeneratorService.generateSequence(Process.SEQUENCE_NAME));
        process.setType(dto.getType());
        process.setRecipeId(recipeId);
        process.setName(dto.getName());
        process.setDescription(dto.getDescription());

        processValidator.validateOrThrow(process);
        Process saved = processRepository.save(process);
        return processMapper.toDTO(saved);
    }

    @Override
    public ProcessResponseDTO get(Long recipeId, Long processId, Long requestingUserId) {
        requireAccessibleRecipe(recipeId, requestingUserId);
        Process process = findProcessOrThrow(processId);
        requireBelongsToRecipe(process, recipeId);
        return processMapper.toDTO(process);
    }

    @Override
    public List<ProcessResponseDTO> listByRecipe(Long recipeId, Long requestingUserId) {
        requireAccessibleRecipe(recipeId, requestingUserId);
        return processRepository.findByRecipeId(recipeId)
                .stream()
                .map(processMapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProcessResponseDTO update(Long recipeId, Long processId, Long userId, ProcessUpdateDTO dto) {
        requireOwnedRecipe(recipeId, userId);
        Process process = findProcessOrThrow(processId);
        requireBelongsToRecipe(process, recipeId);

        process.setName(dto.getName());
        process.setDescription(dto.getDescription());
        if (dto.getNodes() != null) {
            process.setNodes(processMapper.toNodeEntities(dto.getNodes()));
        }
        if (dto.getEdges() != null) {
            process.setEdges(processMapper.toEdgeEntities(dto.getEdges()));
        }
        if (dto.getViewport() != null) {
            process.setViewport(processMapper.toViewportEntity(dto.getViewport()));
        }

        processValidator.validateOrThrow(process);
        Process saved = processRepository.save(process);
        return processMapper.toDTO(saved);
    }

    @Override
    public void delete(Long recipeId, Long processId, Long userId) {
        RecipeTemplate recipe = requireOwnedRecipe(recipeId, userId);
        Process process = findProcessOrThrow(processId);
        requireBelongsToRecipe(process, recipeId);

        if (recipe.getMainProcessId() != null && recipe.getMainProcessId().equals(processId)) {
            throw new ProcessValidationException(
                    "Cannot delete process " + processId + ": it is the recipe's MAIN process (referenced by mainProcessId)");
        }

        processRepository.deleteById(processId);
    }

    @Override
    public ProcessResponseDTO copy(Long recipeId, Long processId, Long userId) {
        requireOwnedRecipe(recipeId, userId);
        Process source = findProcessOrThrow(processId);

        if (!recipeId.equals(source.getRecipeId())) {
            throw new ProcessValidationException(
                    "Cannot copy process " + processId + ": it belongs to a different recipe");
        }
        if (source.getType() == ProcessType.MAIN) {
            throw new ProcessValidationException("The MAIN process cannot be copied");
        }

        Process copy = copyProcess(source);
        return processMapper.toDTO(copy);
    }

    /**
     * Clones {@code original} into a new, independently-owned {@link Process}
     * document with freshly generated node/edge ids, so the copy shares no
     * live reference back to the source. A process is now always a single,
     * self-contained STEP/CONDITION graph — it never embeds another process
     * as a node — so this is a flat clone, not a recursive one.
     *
     * @param original the process to copy
     * @return the saved copy
     */
    private Process copyProcess(Process original) {
        Map<String, String> nodeIdMap = new HashMap<>();
        List<Process.ProcessNode> copiedNodes = new ArrayList<>();
        for (Process.ProcessNode node : safeNodes(original)) {
            Process.ProcessNode nodeCopy = cloneNode(node);
            String newNodeId = UUID.randomUUID().toString();
            nodeIdMap.put(node.getId(), newNodeId);
            nodeCopy.setId(newNodeId);
            copiedNodes.add(nodeCopy);
        }

        List<Process.ProcessEdge> copiedEdges = new ArrayList<>();
        for (Process.ProcessEdge edge : safeEdges(original)) {
            Process.ProcessEdge edgeCopy = cloneEdge(edge);
            edgeCopy.setId(UUID.randomUUID().toString());
            edgeCopy.setSource(nodeIdMap.getOrDefault(edge.getSource(), edge.getSource()));
            edgeCopy.setTarget(nodeIdMap.getOrDefault(edge.getTarget(), edge.getTarget()));
            copiedEdges.add(edgeCopy);
        }

        Process copy = new Process();
        copy.setId(sequenceGeneratorService.generateSequence(Process.SEQUENCE_NAME));
        copy.setType(original.getType());
        copy.setRecipeId(original.getRecipeId());
        copy.setName(original.getName() + COPY_SUFFIX);
        copy.setDescription(original.getDescription());
        copy.setNodes(copiedNodes);
        copy.setEdges(copiedEdges);
        copy.setViewport(cloneViewport(original.getViewport()));

        processValidator.validateOrThrow(copy);
        return processRepository.save(copy);
    }

    private List<Process.ProcessNode> safeNodes(Process process) {
        return process.getNodes() == null ? List.of() : process.getNodes();
    }

    private List<Process.ProcessEdge> safeEdges(Process process) {
        return process.getEdges() == null ? List.of() : process.getEdges();
    }

    private Process.ProcessNode cloneNode(Process.ProcessNode node) {
        Process.ProcessNode copy = new Process.ProcessNode();
        copy.setKind(node.getKind());
        copy.setData(node.getData() != null ? new HashMap<>(node.getData()) : new HashMap<>());
        copy.setType(node.getType());
        copy.setPosition(node.getPosition());
        copy.setMeasured(node.getMeasured());
        copy.setWidth(node.getWidth());
        copy.setHeight(node.getHeight());
        copy.setParentId(node.getParentId());
        copy.setExtent(node.getExtent());
        copy.setDraggable(node.getDraggable());
        copy.setSelectable(node.getSelectable());
        copy.setDeletable(node.getDeletable());
        return copy;
    }

    private Process.ProcessEdge cloneEdge(Process.ProcessEdge edge) {
        Process.ProcessEdge copy = new Process.ProcessEdge();
        copy.setSource(edge.getSource());
        copy.setTarget(edge.getTarget());
        copy.setSourceHandle(edge.getSourceHandle());
        copy.setTargetHandle(edge.getTargetHandle());
        copy.setType(edge.getType());
        copy.setAnimated(edge.getAnimated());
        copy.setStyle(edge.getStyle() != null ? new HashMap<>(edge.getStyle()) : null);
        copy.setData(edge.getData() != null ? new HashMap<>(edge.getData()) : new HashMap<>());
        copy.setLabel(edge.getLabel());
        return copy;
    }

    private Process.ProcessViewport cloneViewport(Process.ProcessViewport viewport) {
        if (viewport == null) {
            return null;
        }
        Process.ProcessViewport copy = new Process.ProcessViewport();
        copy.setX(viewport.getX());
        copy.setY(viewport.getY());
        copy.setZoom(viewport.getZoom());
        return copy;
    }

    private Process findProcessOrThrow(Long processId) {
        return processRepository.findById(processId)
                .orElseThrow(() -> new NoSuchElementException("Process not found: " + processId));
    }

    private void requireBelongsToRecipe(Process process, Long recipeId) {
        if (!recipeId.equals(process.getRecipeId())) {
            throw new NoSuchElementException("Process " + process.getId() + " does not belong to recipe " + recipeId);
        }
    }

    private RecipeTemplate requireOwnedRecipe(Long recipeId, Long userId) {
        RecipeTemplate recipe = recipeTemplateRepository.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
        if (userId == null || !userId.equals(recipe.getCreatedBy())) {
            throw new RecipeAccessDeniedException("You do not have permission to modify this recipe");
        }
        return recipe;
    }

    private RecipeTemplate requireAccessibleRecipe(Long recipeId, Long requestingUserId) {
        RecipeTemplate recipe = recipeTemplateRepository.findById(recipeId)
                .orElseThrow(() -> new NoSuchElementException("Recipe not found: " + recipeId));
        if (recipe.getVisibility() == Visibility.PUBLIC) {
            return recipe;
        }
        if (requestingUserId != null && requestingUserId.equals(recipe.getCreatedBy())) {
            return recipe;
        }
        throw new RecipeAccessDeniedException("You do not have permission to view this recipe");
    }
}
