package com.processVisualisation.virtualKitchen.common.utils;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The cooking order of a {@link Process}'s STEP nodes, shared by everything that needs "the
 * previous step" or "the last step" (visualization prompts, recipe thumbnails).
 */
public final class ProcessStepOrder {

    private ProcessStepOrder() {
    }

    /**
     * Orders STEP nodes by following the whole process graph's edges (topological order across ALL
     * node kinds, not just STEP-to-STEP edges) instead of relying on array position, so previous-step
     * continuity is accurate: two STEPs separated by a CONDITION
     * node would otherwise both get indegree 0 and silently fall back to array position.
     */
    public static List<Process.ProcessNode> orderedSteps(Process process) {
        List<Process.ProcessNode> allNodes = process.getNodes() != null ? process.getNodes() : new ArrayList<>();

        Set<String> allIds = allNodes.stream()
                .map(Process.ProcessNode::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        allIds.forEach(id -> indegree.put(id, 0));

        if (process.getEdges() != null) {
            for (Process.ProcessEdge edge : process.getEdges()) {
                String source = edge.getSource();
                String target = edge.getTarget();
                if (allIds.contains(source) && allIds.contains(target)) {
                    adjacency.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
                    indegree.merge(target, 1, Integer::sum);
                }
            }
        }

        Deque<String> queue = new ArrayDeque<>();
        allIds.forEach(id -> {
            if (indegree.get(id) == 0) queue.add(id);
        });

        List<String> orderedIds = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) continue;
            orderedIds.add(id);
            for (String next : adjacency.getOrDefault(id, List.of())) {
                int nextIndegree = indegree.merge(next, -1, Integer::sum);
                if (nextIndegree <= 0 && !visited.contains(next)) queue.add(next);
            }
        }

        for (String id : allIds) {
            if (!visited.contains(id)) orderedIds.add(id);
        }

        Map<String, Process.ProcessNode> nodeById = allNodes.stream()
                .collect(Collectors.toMap(Process.ProcessNode::getId, node -> node, (a, b) -> a));

        List<Process.ProcessNode> ordered = new ArrayList<>();
        for (String id : orderedIds) {
            Process.ProcessNode node = nodeById.get(id);
            if (node != null && node.getKind() == ProcessNodeKind.STEP) {
                ordered.add(node);
            }
        }
        return ordered;
    }
}
