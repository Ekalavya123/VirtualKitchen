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

/**
 * Default implementation of IProcessTemplateService. Persists recipe templates via
 * RecipeTemplateRepository, enforces ownership (via requireOwner) on mutating operations,
 * and, when a public template is copied to a user library via copyToUser, deep-clones the
 * template saved process-flow graph (nodes, edges, viewport) from RecipeRepository onto the
 * new template so the copy is fully independent of the original. Maps between entities and
 * DTOs via ProcessTemplateMapper.
 */
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

    /**
     * Creates and persists a new recipe template, assigning it a new sequence-generated id.
     *
     * @param dto the template details to persist
     * @return the created template
     */
    @Override
    public RecipeTemplateResponseDTO create(RecipeTemplateRequestDTO dto){
        RecipeTemplate pt = mapper.toEntity(dto);
        pt.setId(seq.generateSequence(RecipeTemplate.SEQUENCE_NAME));
        return mapper.toDTO(repo.save(pt));
    }

    /**
     * Retrieves a single recipe template by id.
     *
     * @param id the id of the template to fetch
     * @return the matching template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     */
    @Override
    public RecipeTemplateResponseDTO get(Long id){
        return mapper.toDTO(repo.findById(id).orElseThrow());
    }

    /**
     * Retrieves all recipe templates owned by a given user.
     *
     * @param userId the id of the owning user to filter by
     * @return the templates created by that user
     */
    @Override
    public List<RecipeTemplateResponseDTO> getByUser(Long userId){
        return repo.findByCreatedBy(userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves all publicly visible recipe templates that were not created by the given user.
     *
     * @param userId the id of the user to exclude as owner
     * @return the matching public templates owned by other users
     */
    @Override
    public List<RecipeTemplateResponseDTO> getGlobalRecipes(Long userId){
        return repo.findByVisibilityAndCreatedByNot(Visibility.PUBLIC, userId)
                .stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Updates the name and description of a recipe template, after verifying the requesting
     * user owns it.
     *
     * @param id     the id of the template to update
     * @param userId the id of the user requesting the update, used for ownership verification
     * @param dto    the new name/description values
     * @return the updated template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if userId does not own the template
     */
    @Override
    public RecipeTemplateResponseDTO update(Long id, Long userId, RecipeTemplateUpdateDTO dto){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setName(dto.getName());
        pt.setDescription(dto.getDescription());
        return mapper.toDTO(repo.save(pt));
    }

    /**
     * Deletes a recipe template, after verifying the requesting user owns it.
     *
     * @param id     the id of the template to delete
     * @param userId the id of the user requesting the deletion, used for ownership verification
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if userId does not own the template
     */
    @Override
    public void delete(Long id, Long userId){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        repo.deleteById(id);
    }

    /**
     * Updates the visibility (e.g. private/public) of a recipe template, after verifying the
     * requesting user owns it.
     *
     * @param id         the id of the template to update
     * @param userId     the id of the user requesting the update, used for ownership verification
     * @param visibility the new visibility value
     * @return the updated template
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if userId does not own the template
     */
    @Override
    public RecipeTemplateResponseDTO updateVisibility(Long id, Long userId, Visibility visibility){
        RecipeTemplate pt = repo.findById(id).orElseThrow();
        requireOwner(pt, userId);
        pt.setVisibility(visibility);
        return mapper.toDTO(repo.save(pt));
    }

    /**
     * Copies a publicly visible recipe template into a new private template owned by the given
     * user, including a deep clone of the original template saved process-flow graph (nodes,
     * edges, viewport), if one exists, via copyFlow.
     *
     * @param id     the id of the public template to copy
     * @param userId the id of the user the copy will be owned by
     * @return the newly created copy
     * @throws java.util.NoSuchElementException if no template exists with the given id
     * @throws RecipeAccessDeniedException if the source template is not Visibility.PUBLIC
     */
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

    /**
     * Verifies that userId is the owner of the given recipe template.
     *
     * @param pt     the template to check ownership of
     * @param userId the id of the user attempting the operation
     * @throws RecipeAccessDeniedException if userId is null or does not match the template owner
     */
    private void requireOwner(RecipeTemplate pt, Long userId){
        if (userId == null || !userId.equals(pt.getCreatedBy())) {
            throw new RecipeAccessDeniedException("You do not have permission to modify this recipe");
        }
    }

    /**
     * If the source recipe template has a saved process-flow graph, deep-clones its nodes,
     * edges, and viewport onto a new Recipe document keyed to the new template id and owner,
     * so the copy renders independently of the original flow.
     *
     * @param originalRecipeId the id of the source template whose flow graph is cloned
     * @param newRecipeId      the id of the newly created template the cloned flow is attached to
     * @param newOwnerId       the id of the user who will own the cloned flow
     */
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

    /**
     * Deep-clones a list of flow graph nodes, copying every field onto new
     * Recipe.NodeDocument instances.
     *
     * @param nodes the nodes to clone, may be null
     * @return a new list of cloned nodes, empty if nodes was null
     */
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

    /**
     * Deep-clones a list of flow graph edges, copying every field onto new
     * Recipe.EdgeDocument instances.
     *
     * @param edges the edges to clone, may be null
     * @return a new list of cloned edges, empty if edges was null
     */
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

    /**
     * Deep-clones a flow graph viewport (pan/zoom state) onto a new
     * Recipe.ViewportDocument instance.
     *
     * @param viewport the viewport to clone, may be null
     * @return a new cloned viewport, or null if viewport was null
     */
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
