package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.common.exception.RecipeAccessDeniedException;
import com.processVisualisation.virtualKitchen.common.mapper.ProcessTemplateMapper;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.recipe.model.RecipeTemplate;
import com.processVisualisation.virtualKitchen.recipe.model.Visibility;
import com.processVisualisation.virtualKitchen.common.SequenceGeneratorService;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeTemplateUpdateDTO;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeTemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RecipeTemplateServiceImpl implements IProcessTemplateService {

    @Autowired
    private RecipeTemplateRepository repo;

    @Autowired
    private ProcessTemplateMapper mapper;

    @Autowired
    private SequenceGeneratorService seq;

    @Autowired
    private RecipeRepository recipeRepository;

    @Override
    public RecipeTemplateResponseDTO create(RecipeTemplateRequestDTO dto){
        RecipeTemplate pt = mapper.toEntity(dto);
        pt.setId(seq.generateSequence(RecipeTemplate.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public RecipeTemplateResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    @Override
    public List<RecipeTemplateResponseDTO> getByUser(Long userId){
        return repo.findByCreatedBy(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RecipeTemplateResponseDTO> getGlobalRecipes(Long userId){
        return repo.findByVisibilityAndCreatedByNot(Visibility.PUBLIC, userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public RecipeTemplateResponseDTO update(Long id, Long userId, RecipeTemplateUpdateDTO dto){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public void delete(Long id, Long userId){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        repo.deleteById(id);
    }

    @Override
    public RecipeTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setVisibility(visibility);
        return mapper.toDTO(repo.save(pt));
    }

    @Override
    public RecipeTemplateResponseDTO copyToUser(Long id, Long userId){
        RecipeTemplate original = repo.findById(id).orElseThrow();

        if (original.getVisibility() != Visibility.PUBLIC) {
            throw new RecipeAccessDeniedException("Only public recipes can be added to My Recipes");
        }

        RecipeTemplate copy = new RecipeTemplate();
        copy.setId(seq.generateSequence(RecipeTemplate.SEQUENCE_NAME));
        copy.setName(original.getName());
        copy.setDescription(original.getDescription());
        copy.setCreatedBy(userId);
        copy.setVisibility(Visibility.PRIVATE);

        RecipeTemplate saved = repo.save(copy);

        copyFlow(original.getId(), saved.getId(), userId);

        return mapper.toDTO(saved);
    }

    private void requireOwner(RecipeTemplate pt, Long userId){
        if (userId == null || !userId.equals(pt.getCreatedBy())) {
            throw new RecipeAccessDeniedException("You do not have permission to modify this recipe");
        }
    }

    private void copyFlow(Long originalRecipeId, Long newRecipeId, Long newOwnerId){
        recipeRepository.findByFlowId(String.valueOf(originalRecipeId)).ifPresent(originalFlow -> {
            Recipe copyFlow = new Recipe();
            copyFlow.setFlowId(String.valueOf(newRecipeId));
            copyFlow.setUserId(String.valueOf(newOwnerId));
            copyFlow.setTemplateId(newRecipeId);
            copyFlow.setNodes(cloneNodes(originalFlow.getNodes()));
            copyFlow.setEdges(cloneEdges(originalFlow.getEdges()));
            copyFlow.setViewport(cloneViewport(originalFlow.getViewport()));
            recipeRepository.save(copyFlow);
        });
    }

    private List<Recipe.NodeDocument> cloneNodes(List<Recipe.NodeDocument> nodes){
        List<Recipe.NodeDocument> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }

        for (Recipe.NodeDocument node : nodes) {
            Recipe.NodeDocument copy = new Recipe.NodeDocument();
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

    private List<Recipe.EdgeDocument> cloneEdges(List<Recipe.EdgeDocument> edges){
        List<Recipe.EdgeDocument> result = new ArrayList<>();
        if (edges == null) {
            return result;
        }

        for (Recipe.EdgeDocument edge : edges) {
            Recipe.EdgeDocument copy = new Recipe.EdgeDocument();
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

    private Recipe.ViewportDocument cloneViewport(Recipe.ViewportDocument viewport){
        if (viewport == null) {
            return null;
        }

        Recipe.ViewportDocument copy = new Recipe.ViewportDocument();
        copy.setX(viewport.getX());
        copy.setY(viewport.getY());
        copy.setZoom(viewport.getZoom());
        return copy;
    }
}

