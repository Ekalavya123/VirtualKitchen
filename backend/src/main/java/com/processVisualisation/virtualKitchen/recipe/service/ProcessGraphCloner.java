package com.processVisualisation.virtualKitchen.recipe.service;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.StepAnnotations;

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
        return cloneGraph(original, processIdMap, null);
    }

    /**
     * As {@link #cloneGraph(Process, Map)}, for a whole recipe copied at once: {@code nodeIdMaps} holds every
     * source process's old-to-new node ids (see {@link #newNodeIds}), so a reference to a step of another process
     * ({@code actionOn.processes[].stepId}, a use of a Preparation step's output) follows that step's copy too.
     */
    public static Process cloneGraph(Process original, Map<Long, Long> processIdMap, Map<Long, Map<String, String>> nodeIdMaps) {
        Map<String, String> nodeIdMap = nodeIdMaps != null && nodeIdMaps.containsKey(original.getId())
                ? nodeIdMaps.get(original.getId())
                : newNodeIds(original);

        List<Process.ProcessNode> copiedNodes = new ArrayList<>();
        for (Process.ProcessNode node : nodes(original)) {
            Process.ProcessNode nodeCopy = cloneNode(node);
            nodeCopy.setId(nodeIdMap.get(node.getId()));
            if (node.getParentId() != null) {
                nodeCopy.setParentId(nodeIdMap.getOrDefault(node.getParentId(), node.getParentId()));
            }
            Map<String, Object> data = deepCopy(node.getData());
            if (processIdMap != null) {
                remapActionOnProcessRefs(data, processIdMap, nodeIdMaps);
            }
            remapActionOnStepRefs(data, nodeIdMap);
            remapDescriptionAnnotations(data, processIdMap, nodeIdMaps, nodeIdMap);
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

    /** Fresh ids for every node of {@code original}, keyed by its current node ids. */
    public static Map<String, String> newNodeIds(Process original) {
        Map<String, String> nodeIdMap = new HashMap<>();
        for (Process.ProcessNode node : nodes(original)) {
            nodeIdMap.put(node.getId(), UUID.randomUUID().toString());
        }
        return nodeIdMap;
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
     * the shape the frontend's recipe-tool/model/recipeStepData.ts writes) to the copied processes, and a referenced
     * step ({@code stepId}) to its copy when {@code nodeIdMaps} knows it. A reference to a process
     * that isn't in {@code processIdMap} — i.e. not part of the recipe being copied — is dropped
     * rather than kept, since keeping it would be a live link from the copy into another recipe.
     */
    @SuppressWarnings("unchecked")
    private static void remapActionOnProcessRefs(Map<String, Object> data, Map<Long, Long> processIdMap, Map<Long, Map<String, String>> nodeIdMaps) {
        if (!(data.get("step") instanceof Map<?, ?> step)) return;
        if (!(step.get("actionOn") instanceof Map<?, ?> actionOn)) return;
        if (!(actionOn.get("processes") instanceof List<?> references)) return;

        List<Object> remapped = new ArrayList<>();
        for (Object reference : references) {
            if (!(reference instanceof Map<?, ?> entry)) continue;
            Long oldId = asLong(entry.get("processId"));
            Long newId = processIdMap.get(oldId);
            if (newId == null) continue;
            Map<String, Object> entryCopy = new LinkedHashMap<>((Map<String, Object>) entry);
            entryCopy.put("processId", newId);
            Object stepId = entry.get("stepId");
            Map<String, String> stepIds = nodeIdMaps == null ? null : nodeIdMaps.get(oldId);
            if (stepId != null && stepIds != null) {
                entryCopy.put("stepId", stepIds.getOrDefault(String.valueOf(stepId), String.valueOf(stepId)));
            }
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

    /**
     * Points the @ annotations in a STEP's description at the copies, like the Action On references they mirror:
     * a prepared annotation follows its process (and step) — or turns back into plain text when that process isn't
     * part of the copy — and a step annotation follows its node. Ingredient ids don't change in a copy.
     */
    @SuppressWarnings("unchecked")
    private static void remapDescriptionAnnotations(Map<String, Object> data, Map<Long, Long> processIdMap,
                                                    Map<Long, Map<String, String>> nodeIdMaps, Map<String, String> nodeIdMap) {
        if (!(data.get("step") instanceof Map<?, ?> step)) return;
        if (!(step.get("actionDescription") instanceof String description) || !StepAnnotations.hasAnnotations(description)) return;
        String remapped = StepAnnotations.remapIds(description, annotation -> switch (annotation.kind()) {
            case StepAnnotations.STEP -> nodeIdMap.getOrDefault(annotation.id(), annotation.id());
            case StepAnnotations.PREPARED -> {
                if (processIdMap == null) yield annotation.id();
                int separator = annotation.id().indexOf(':');
                Long oldId = asLong(separator < 0 ? annotation.id() : annotation.id().substring(0, separator));
                Long newId = oldId == null ? null : processIdMap.get(oldId);
                if (newId == null) yield null;
                if (separator < 0) yield String.valueOf(newId);
                String stepId = annotation.id().substring(separator + 1);
                Map<String, String> stepIds = nodeIdMaps == null ? null : nodeIdMaps.get(oldId);
                yield newId + ":" + (stepIds == null ? stepId : stepIds.getOrDefault(stepId, stepId));
            }
            default -> annotation.id();
        });
        ((Map<String, Object>) step).put("actionDescription", remapped);
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
