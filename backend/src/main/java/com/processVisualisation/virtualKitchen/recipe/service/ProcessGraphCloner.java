package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.model.Process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Deep-clones a process's graph (nodes, edges, viewport) for the copy features, so a copy shares no mutable
 * state with its source: every node and edge gets a new id, node/edge data is deep-copied, and the references
 * that point inside the graph (edge ends, parent nodes, a STEP's Action On step outputs) follow the new ids.
 */
public final class ProcessGraphCloner {

    private ProcessGraphCloner() {
    }

    /**
     * Clones {@code original}'s graph into a new {@link Process} carrying only nodes, edges and viewport; the
     * caller sets its id, recipe, type, name and description.
     *
     * @param processIdMap source-to-copy process ids when a whole recipe is copied: every STEP's Action On
     *                     subprocess reference is rewritten through it, and a reference to a process outside it
     *                     is dropped. Null when copying within the same recipe, which keeps references as they are.
     */
    public static Process cloneGraph(Process original, Map<Long, Long> processIdMap) {
        Map<String, String> nodeIdMap = new HashMap<>();
        for (Process.ProcessNode node : nodes(original)) {
            nodeIdMap.put(node.getId(), UUID.randomUUID().toString());
        }

        List<Process.ProcessNode> copiedNodes = new ArrayList<>();
        for (Process.ProcessNode node : nodes(original)) {
            Process.ProcessNode nodeCopy = cloneNode(node);
            nodeCopy.setId(nodeIdMap.get(node.getId()));
            if (node.getParentId() != null) {
                nodeCopy.setParentId(nodeIdMap.getOrDefault(node.getParentId(), node.getParentId()));
            }
            Map<String, Object> data = deepCopy(node.getData());
            if (processIdMap != null) {
                remapActionOnProcessRefs(data, processIdMap);
            }
            remapActionOnStepRefs(data, nodeIdMap);
            nodeCopy.setData(data);
            copiedNodes.add(nodeCopy);
        }

        List<Process.ProcessEdge> copiedEdges = new ArrayList<>();
        for (Process.ProcessEdge edge : edges(original)) {
            Process.ProcessEdge edgeCopy = cloneEdge(edge);
            edgeCopy.setId(UUID.randomUUID().toString());
            edgeCopy.setSource(nodeIdMap.getOrDefault(edge.getSource(), edge.getSource()));
            edgeCopy.setTarget(nodeIdMap.getOrDefault(edge.getTarget(), edge.getTarget()));
            copiedEdges.add(edgeCopy);
        }

        Process copy = new Process();
        copy.setNodes(copiedNodes);
        copy.setEdges(copiedEdges);
        copy.setViewport(cloneViewport(original.getViewport()));
        return copy;
    }

    /** A deep copy of a node/edge data map (nested maps and lists copied, leaf values shared). Null gives an empty map. */
    public static Map<String, Object> deepCopy(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        if (source != null) {
            source.forEach((key, value) -> copy.put(key, deepCopyValue(value)));
        }
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nested) -> copy.put(String.valueOf(key), deepCopyValue(nested)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            list.forEach(nested -> copy.add(deepCopyValue(nested)));
            return copy;
        }
        return value;
    }

    /**
     * Rewrites a STEP's Action On subprocess references ({@code data.step.actionOn.processes[].processId},
     * the shape the frontend's recipe-tool/process/model/recipeStepData.ts writes) to the copied processes. A reference to a process
     * that isn't in {@code processIdMap} — i.e. not part of the recipe being copied — is dropped
     * rather than kept, since keeping it would be a live link from the copy into another recipe.
     */
    @SuppressWarnings("unchecked")
    private static void remapActionOnProcessRefs(Map<String, Object> data, Map<Long, Long> processIdMap) {
        if (!(data.get("step") instanceof Map<?, ?> step)) return;
        if (!(step.get("actionOn") instanceof Map<?, ?> actionOn)) return;
        if (!(actionOn.get("processes") instanceof List<?> references)) return;

        List<Object> remapped = new ArrayList<>();
        for (Object reference : references) {
            if (!(reference instanceof Map<?, ?> entry)) continue;
            Long newId = processIdMap.get(asLong(entry.get("processId")));
            if (newId == null) continue;
            Map<String, Object> entryCopy = new LinkedHashMap<>((Map<String, Object>) entry);
            entryCopy.put("processId", newId);
            remapped.add(entryCopy);
        }
        ((Map<String, Object>) actionOn).put("processes", remapped);
    }

    /**
     * Rewrites a STEP's Action On step-output references ({@code data.step.actionOn.steps[].stepId})
     * to the copied nodes' new ids, since a copy regenerates every node id. A reference to a node
     * outside this process is kept as-is, so {@code ProcessValidator} still reports it.
     */
    @SuppressWarnings("unchecked")
    private static void remapActionOnStepRefs(Map<String, Object> data, Map<String, String> nodeIdMap) {
        if (!(data.get("step") instanceof Map<?, ?> step)) return;
        if (!(step.get("actionOn") instanceof Map<?, ?> actionOn)) return;
        if (!(actionOn.get("steps") instanceof List<?> references)) return;

        List<Object> remapped = new ArrayList<>();
        for (Object reference : references) {
            if (!(reference instanceof Map<?, ?> entry)) continue;
            Map<String, Object> entryCopy = new LinkedHashMap<>((Map<String, Object>) entry);
            Object stepId = entry.get("stepId");
            if (stepId != null) entryCopy.put("stepId", nodeIdMap.getOrDefault(String.valueOf(stepId), String.valueOf(stepId)));
            remapped.add(entryCopy);
        }
        ((Map<String, Object>) actionOn).put("steps", remapped);
    }

    private static Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static List<Process.ProcessNode> nodes(Process process) {
        return process.getNodes() == null ? List.of() : process.getNodes();
    }

    private static List<Process.ProcessEdge> edges(Process process) {
        return process.getEdges() == null ? List.of() : process.getEdges();
    }

    private static Process.ProcessNode cloneNode(Process.ProcessNode node) {
        Process.ProcessNode copy = new Process.ProcessNode();
        copy.setKind(node.getKind());
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

    private static Process.ProcessEdge cloneEdge(Process.ProcessEdge edge) {
        Process.ProcessEdge copy = new Process.ProcessEdge();
        copy.setSource(edge.getSource());
        copy.setTarget(edge.getTarget());
        copy.setSourceHandle(edge.getSourceHandle());
        copy.setTargetHandle(edge.getTargetHandle());
        copy.setType(edge.getType());
        copy.setAnimated(edge.getAnimated());
        copy.setStyle(edge.getStyle() != null ? deepCopy(edge.getStyle()) : null);
        copy.setData(deepCopy(edge.getData()));
        copy.setLabel(edge.getLabel());
        return copy;
    }

    private static Process.ProcessViewport cloneViewport(Process.ProcessViewport viewport) {
        if (viewport == null) {
            return null;
        }
        Process.ProcessViewport copy = new Process.ProcessViewport();
        copy.setX(viewport.getX());
        copy.setY(viewport.getY());
        copy.setZoom(viewport.getZoom());
        return copy;
    }
}
