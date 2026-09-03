package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateMapper;
import com.processVisualisation.virtualKitchen.recipe.model.FlowDocument;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.ProcessTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.FlowRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.ProcessTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProcessTemplateServiceImpl implements IProcessTemplateService {

    @Autowired
    private ProcessTemplateRepository repo;

    @Autowired
    private ProcessTemplateMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Autowired
    private FlowRepository flowRepository;

    @Override
    public ProcessTemplateResponseDTO create(ProcessTemplateRequestDTO dto){
        ProcessTemplate pt = mapper.toEntity(dto);
        pt.setId(seq.generateSequence(ProcessTemplate.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public ProcessTemplateResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    @Override
    public List<ProcessTemplateResponseDTO> getByUser(Long userId){
        return repo.findByCreatedBy(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProcessTemplateResponseDTO> getGlobalRecipes(Long userId){
        return repo.findByVisibilityAndCreatedByNot(Visibility.PUBLIC, userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProcessTemplateResponseDTO update(Long id, Long userId, ProcessTemplateUpdateDTO dto){
        ProcessTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public void delete(Long id, Long userId){
        ProcessTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        repo.deleteById(id);
    }

    @Override
    public ProcessTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility){
        ProcessTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setVisibility(visibility);
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public ProcessTemplateResponseDTO copyToUser(Long id, Long userId){
        ProcessTemplate original = repo.findById(id).orElseThrow();

        if (original.getVisibility() != Visibility.PUBLIC) {
            throw new RecipeAccessDeniedException("Only public recipes can be added to My Recipes");
        }

        ProcessTemplate copy = new ProcessTemplate();
        copy.setId(seq.generateSequence(ProcessTemplate.SEQUENCE_NAME));
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setCreatedBy(userId);
        copy.setVisibility(Visibility.PRIVATE);

        ProcessTemplate saved = repo.save(copy);

        copyFlow(original.getId(), saved.getId(), userId);

        return mapper.toDTO(saved);
    }

    private void requireOwner(ProcessTemplate pt, Long userId){
        if (userId == null || !userId.equals(pt.getCreatedBy())) {
            throw new RecipeAccessDeniedException("You do not have permission to modify this recipe");
        }
    }

    private void copyFlow(Long originalRecipeId, Long newRecipeId, Long newOwnerId){
        flowRepository.findByFlowId(String.valueOf(originalRecipeId)).ifPresent(originalFlow -> {
            FlowDocument copyFlow = new FlowDocument();
            copyFlow.setFlowId(String.valueOf(newRecipeId));
            copyFlow.setUserId(String.valueOf(newOwnerId));
            copyFlow.setTemplateId(newRecipeId);
            copyFlow.setNodes(cloneNodes(originalFlow.getNodes()));
            copyFlow.setEdges(cloneEdges(originalFlow.getEdges()));
            copyFlow.setViewport(cloneViewport(originalFlow.getViewport()));
            flowRepository.save(copyFlow);
        });
    }

    private List<FlowDocument.NodeDocument> cloneNodes(List<FlowDocument.NodeDocument> nodes){
        List<FlowDocument.NodeDocument> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }

        for (FlowDocument.NodeDocument node : nodes) {
            FlowDocument.NodeDocument copy = new FlowDocument.NodeDocument();
            copy.setId(node.getId());
            copy.setType(node.getType());
            copy.setData(node.getData() != null ? new LinkedHashMap<>(node.getData()) : new LinkedHashMap<>());
            copy.setPosition(node.getPosition());
            copy.setMeasured(node.getMeasured());
            copy.setWidth(node.getWidth());
            copy.setHeight(node.getHeight());
            copy.setParentId(node.getParentId());
            copy.setExtent(node.getExtent());
            copy.setDraggable(node.getDraggable());
            copy.setSelectable(node.getSelectable());
            copy.setDeletable(node.getDeletable());
            result.add(copy);
        }

        return result;
    }

    private List<FlowDocument.EdgeDocument> cloneEdges(List<FlowDocument.EdgeDocument> edges){
        List<FlowDocument.EdgeDocument> result = new ArrayList<>();
        if (edges == null) {
            return result;
        }

        for (FlowDocument.EdgeDocument edge : edges) {
            FlowDocument.EdgeDocument copy = new FlowDocument.EdgeDocument();
            copy.setId(edge.getId());
            copy.setSource(edge.getSource());
            copy.setTarget(edge.getTarget());
            copy.setSourceHandle(edge.getSourceHandle());
            copy.setTargetHandle(edge.getTargetHandle());
            copy.setType(edge.getType());
            copy.setAnimated(edge.getAnimated());
            copy.setStyle(edge.getStyle() != null ? new LinkedHashMap<>(edge.getStyle()) : null);
            copy.setData(edge.getData() != null ? new LinkedHashMap<>(edge.getData()) : new LinkedHashMap<>());
            copy.setLabel(edge.getLabel());
            result.add(copy);
        }

        return result;
    }

    private FlowDocument.ViewportDocument cloneViewport(FlowDocument.ViewportDocument viewport){
        if (viewport == null) {
            return null;
        }

        FlowDocument.ViewportDocument copy = new FlowDocument.ViewportDocument();
        copy.setX(viewport.getX());
        copy.setY(viewport.getY());
        copy.setZoom(viewport.getZoom());
        return copy;
    }
}

