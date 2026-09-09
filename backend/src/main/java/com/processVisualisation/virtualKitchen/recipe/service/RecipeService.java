package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeSaveResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for persisting and retrieving the React Flow-style process visualization graph
 * (nodes, edges, viewport) for a recipe. Converts the loosely-typed JSON-like maps received
 * from the frontend into typed Recipe document sub-objects (NodeDocument, EdgeDocument,
 * ViewportDocument) before saving via RecipeRepository, and returns the stored document as-is
 * on read.
 */
@Service
public class RecipeService {

    private final RecipeRepository recipeRepository;

    public RecipeService(RecipeRepository recipeRepository) {
        this.recipeRepository = recipeRepository;
    }

    /**
     * Creates or updates the saved flow document for the request flow id: if a document already
     * exists for that flow id it is updated in place, otherwise a new one is created. Nodes,
     * edges, and viewport are converted from raw maps into typed sub-documents before saving.
     *
     * @param request the flow payload (flow id, user id, template id, nodes, edges, viewport) to persist
     * @return a response containing the saved flow id, user id, and a confirmation message
     */
    public RecipeSaveResponseDTO saveFlow(RecipeFlowSaveRequestDTO request) {
        Recipe document = Optional.ofNullable(request.getFlowId())
                .flatMap(recipeRepository::findByFlowId)
                .orElseGet(Recipe::new);

        document.setFlowId(request.getFlowId());
        document.setUserId(request.getUserId());
        document.setTemplateId(request.getTemplateId());
        document.setNodes(mapNodes(request.getNodes()));
        document.setEdges(mapEdges(request.getEdges()));
        document.setViewport(mapViewport(request.getViewport()));

        recipeRepository.save(document);

        RecipeSaveResponseDTO response = new RecipeSaveResponseDTO();
        response.setFlowId(request.getFlowId());
        response.setUserId(request.getUserId());
        response.setMessage("Flow saved successfully");
        return response;
    }

    /**
     * Retrieves the saved flow document for a given flow id.
     *
     * @param flowId the id of the flow to look up
     * @return the matching Recipe document, if one has been saved under that flow id
     */
    public Optional<Recipe> getFlow(String flowId) {
        return recipeRepository.findByFlowId(flowId);
    }

    /**
     * Converts a list of raw node maps (as received from the frontend flow editor) into typed
     * NodeDocument instances, extracting nested position and measured-size objects.
     *
     * @param nodes the raw node maps to convert, may be null
     * @return the converted list of node documents, empty if nodes was null
     */
    private List<Recipe.NodeDocument> mapNodes(List<Map<String, Object>> nodes) {
        List<Recipe.NodeDocument> result = new ArrayList<>();
        if (nodes == null) {
            return result;
        }

        for (Map<String, Object> node : nodes) {
            Recipe.NodeDocument document = new Recipe.NodeDocument();
            document.setId((String) node.get("id"));
            document.setType((String) node.get("type"));
            document.setData(extractObject(node, "data"));

            Map<String, Object> position = extractObject(node, "position");
            if (position != null) {
                Recipe.PositionDocument positionDocument = new Recipe.PositionDocument();
                positionDocument.setX(toDouble(position.get("x")));
                positionDocument.setY(toDouble(position.get("y")));
                document.setPosition(positionDocument);
            }

            Map<String, Object> measured = extractObject(node, "measured");
            if (measured != null) {
                Recipe.MeasuredDocument measuredDocument = new Recipe.MeasuredDocument();
                measuredDocument.setWidth(toDouble(measured.get("width")));
                measuredDocument.setHeight(toDouble(measured.get("height")));
                document.setMeasured(measuredDocument);
            }

            document.setWidth(toDouble(node.get("width")));
            document.setHeight(toDouble(node.get("height")));

            document.setParentId((String) node.get("parentId"));
            document.setExtent((String) node.get("extent"));
            document.setDraggable((Boolean) node.get("draggable"));
            document.setSelectable((Boolean) node.get("selectable"));
            document.setDeletable((Boolean) node.get("deletable"));
            result.add(document);
        }
        return result;
    }

    /**
     * Converts a list of raw edge maps (as received from the frontend flow editor) into typed
     * EdgeDocument instances.
     *
     * @param edges the raw edge maps to convert, may be null
     * @return the converted list of edge documents, empty if edges was null
     */
    private List<Recipe.EdgeDocument> mapEdges(List<Map<String, Object>> edges) {
        List<Recipe.EdgeDocument> result = new ArrayList<>();
        if (edges == null) {
            return result;
        }

        for (Map<String, Object> edge : edges) {
            Recipe.EdgeDocument document = new Recipe.EdgeDocument();
            document.setId((String) edge.get("id"));
            document.setSource((String) edge.get("source"));
            document.setTarget((String) edge.get("target"));
            document.setSourceHandle((String) edge.get("sourceHandle"));
            document.setTargetHandle((String) edge.get("targetHandle"));
            document.setType((String) edge.get("type"));
            document.setAnimated((Boolean) edge.get("animated"));
            document.setStyle(extractObject(edge, "style"));
            document.setData(extractObject(edge, "data"));
            document.setLabel((String) edge.get("label"));
            result.add(document);
        }
        return result;
    }

    /**
     * Extracts the value under the given key as a Map, normalizing its keys to Strings, if the
     * value is itself a Map.
     *
     * @param source the source map to read from
     * @param key    the key whose value should be extracted
     * @return the extracted map with String keys, or null if the value under key is not a Map
     */
    private Map<String, Object> extractObject(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return null;
    }

    /**
     * Converts a raw viewport map (pan/zoom state) into a typed ViewportDocument.
     *
     * @param viewport the raw viewport map to convert, may be null
     * @return the converted viewport document, or null if viewport was null
     */
    private Recipe.ViewportDocument mapViewport(Map<String, Object> viewport) {
        if (viewport == null) {
            return null;
        }

        Recipe.ViewportDocument document = new Recipe.ViewportDocument();
        document.setX(toDouble(viewport.get("x")));
        document.setY(toDouble(viewport.get("y")));
        document.setZoom(toDouble(viewport.get("zoom")));
        return document;
    }

    /**
     * Converts a raw numeric value into a Double.
     *
     * @param value the value to convert, expected to be a Number or null
     * @return the value as a Double, or null if value is not a Number
     */
    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }
}
