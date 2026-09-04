package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveRequestDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.RecipeFlowSaveResponseDTO;
import com.processVisualisation.virtualKitchen.recipe.model.Recipe;
import com.processVisualisation.virtualKitchen.recipe.repository.RecipeRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RecipeService {

    private final RecipeRepository flowRepository;

    public RecipeService(RecipeRepository flowRepository) {
        this.flowRepository = flowRepository;
    }

    public RecipeFlowSaveResponseDTO saveFlow(RecipeFlowSaveRequestDTO request) {
        Recipe document = Optional.ofNullable(request.getFlowId())
                .flatMap(flowRepository::findByFlowId)
                .orElseGet(Recipe::new);

        document.setFlowId(request.getFlowId());
        document.setUserId(request.getUserId());
        document.setTemplateId(request.getTemplateId());
        document.setNodes(mapNodes(request.getNodes()));
        document.setEdges(mapEdges(request.getEdges()));
        document.setViewport(mapViewport(request.getViewport()));

        flowRepository.save(document);

        RecipeFlowSaveResponseDTO response = new RecipeFlowSaveResponseDTO();
        response.setFlowId(request.getFlowId());
        response.setUserId(request.getUserId());
        response.setMessage("Flow saved successfully");
        return response;
    }

    public Optional<Recipe> getFlow(String flowId) {
        return flowRepository.findByFlowId(flowId);
    }

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

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }
}
