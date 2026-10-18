package com.processVisualisation.virtualKitchen.recipe.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The flows of a recipe's Preparation (its one SUBPROCESS): weakly connected groups of nodes, each making one named
 * component — its output is the final STEP's Expected Output. The frontend twin is
 * features/recipe-tool/model/preparationFlows.ts. Used by readiness (RecipeReadinessEvaluator) and by visualization
 * (one image per flow).
 */
public final class PreparationFlows {

    /** The handle of a check's "No" edge — it loops back and never moves a flow forward. */
    public static final String CONDITION_NO_HANDLE = "condition-no";

    /** One flow: its nodes (in node order), the STEPs it ends with, and the step naming its output (may be null). */
    public record Flow(List<Process.ProcessNode> nodes, List<Process.ProcessNode> endSteps, Process.ProcessNode output) {

        /** The flow's named output (its output step's Expected Output), or "" when it has none yet. */
        public String outputName() {
            return output == null ? "" : expectedOutputOf(output);
        }
    }

    private PreparationFlows() {
    }

    /** Each flow of a Preparation, with the STEPs it ends with and the step naming its output. */
    public static List<Flow> flowsOf(Process preparation) {
        Map<String, List<String>> forward = new HashMap<>();
        for (Process.ProcessEdge edge : edgesOf(preparation)) {
            if (CONDITION_NO_HANDLE.equals(edge.getSourceHandle()) || Objects.equals(edge.getSource(), edge.getTarget())) continue;
            forward.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
        }
        List<Flow> flows = new ArrayList<>();
        for (List<Process.ProcessNode> component : componentsOf(preparation)) {
            Map<String, Process.ProcessNode> byId = new HashMap<>();
            component.forEach(node -> byId.put(node.getId(), node));
            List<Process.ProcessNode> endSteps = component.stream()
                    .filter(node -> node.getKind() == ProcessNodeKind.STEP && !reachesAnotherStep(node, forward, byId))
                    .toList();
            Process.ProcessNode output = endSteps.stream().filter(step -> !expectedOutputOf(step).isEmpty()).findFirst()
                    .orElse(endSteps.isEmpty() ? null : endSteps.get(0));
            flows.add(new Flow(component, endSteps, output));
        }
        return flows;
    }

    /** Weakly connected components, nodes in node order, components ordered by their first node. */
    public static List<List<Process.ProcessNode>> componentsOf(Process process) {
        List<Process.ProcessNode> nodes = process.getNodes() == null ? List.of() : process.getNodes().stream().filter(Objects::nonNull).toList();
        Map<String, String> parent = new HashMap<>();
        nodes.forEach(node -> parent.put(node.getId(), node.getId()));
        for (Process.ProcessEdge edge : edgesOf(process)) {
            if (!parent.containsKey(edge.getSource()) || !parent.containsKey(edge.getTarget())) continue;
            parent.put(find(parent, edge.getSource()), find(parent, edge.getTarget()));
        }
        Map<String, List<Process.ProcessNode>> groups = new LinkedHashMap<>();
        nodes.forEach(node -> groups.computeIfAbsent(find(parent, node.getId()), key -> new ArrayList<>()).add(node));
        return new ArrayList<>(groups.values());
    }

    /** A STEP's Expected Output ({@code data.step.expectedOutput}, or flat on {@code data} in older data), trimmed. */
    public static String expectedOutputOf(Process.ProcessNode node) {
        Map<String, Object> data = node.getData() == null ? Map.of() : node.getData();
        Map<?, ?> fields = data.get("step") instanceof Map<?, ?> step ? step : data;
        return fields.get("expectedOutput") instanceof String text ? text.trim() : "";
    }

    private static boolean reachesAnotherStep(Process.ProcessNode start, Map<String, List<String>> forward, Map<String, Process.ProcessNode> byId) {
        Set<String> seen = new HashSet<>(Set.of(start.getId()));
        Deque<String> queue = new ArrayDeque<>(forward.getOrDefault(start.getId(), List.of()));
        while (!queue.isEmpty()) {
            String next = queue.poll();
            if (!seen.add(next)) continue;
            Process.ProcessNode node = byId.get(next);
            if (node != null && node.getKind() == ProcessNodeKind.STEP) return true;
            queue.addAll(forward.getOrDefault(next, List.of()));
        }
        return false;
    }

    private static String find(Map<String, String> parent, String id) {
        String root = id;
        while (!parent.get(root).equals(root)) root = parent.get(root);
        parent.put(id, root);
        return root;
    }

    private static List<Process.ProcessEdge> edgesOf(Process process) {
        return process.getEdges() == null ? List.of() : process.getEdges().stream().filter(Objects::nonNull).toList();
    }
}
