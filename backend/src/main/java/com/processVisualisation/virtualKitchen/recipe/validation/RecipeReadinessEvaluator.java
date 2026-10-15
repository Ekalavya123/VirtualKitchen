package com.processVisualisation.virtualKitchen.recipe.validation;

import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessNodeKind;
import com.processVisualisation.virtualKitchen.recipe.model.ProcessType;
import com.processVisualisation.virtualKitchen.store.catalog.StepCatalogResource;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Full-recipe readiness: whether a recipe's processes form the Preparation + Main structure that whole-recipe
 * operations (ordering, visualizing/narrating every process) rely on — the MAIN process is one connected cooking
 * sequence whose steps have an action and an instruction; the recipe's one SUBPROCESS is its Preparation, holding
 * independent flows that each name their output with their final step's Expected Output, which main steps use by
 * that step's id ({@code actionOn.processes[{processId, stepId}]}).
 * <p>
 * Separate from {@link ProcessValidator} on purpose: an incomplete recipe always saves; readiness only gates the
 * operations that need the whole structure. Issues are BLOCKERs or WARNINGs (shown by the editor, never blocking).
 * The frontend runs the same rules live (features/recipe-tool/model/recipeReadiness.ts); both are tested against
 * the shared cases in src/test/resources/readiness/readiness-cases.json.
 */
@Component
public class RecipeReadinessEvaluator {

    public enum Severity { BLOCKER, WARNING }

    public record Issue(String code, Severity severity, Long processId, String nodeId, String message) {}

    public record Readiness(List<Issue> issues) {
        public List<Issue> blockers() {
            return issues.stream().filter(issue -> issue.severity() == Severity.BLOCKER).toList();
        }

        public boolean ready() {
            return blockers().isEmpty();
        }
    }

    /** A use of another process's output: one of its steps, or (legacy data) the whole process. */
    private record Reference(long processId, String stepId) {}

    /** One preparation flow: its nodes, the STEPs it ends with, and the step naming its output (may be null). */
    private record Flow(List<Process.ProcessNode> nodes, List<Process.ProcessNode> endSteps, Process.ProcessNode output) {}

    private static final String CUSTOM_ID = StepCatalogResource.CUSTOM_ID;
    /** The handle of a check's "No" edge — it loops back and never moves a flow forward. */
    private static final String CONDITION_NO_HANDLE = "condition-no";

    private final Set<String> actionIds;

    public RecipeReadinessEvaluator() {
        Set<String> ids = new HashSet<>();
        StepCatalogResource.read().path("actions").forEach(action -> ids.add(action.path("id").asText()));
        this.actionIds = Set.copyOf(ids);
    }

    public Readiness evaluate(Collection<Process> processes) {
        List<Issue> issues = new ArrayList<>();
        Process main = processes.stream().filter(process -> process.getType() == ProcessType.MAIN).findFirst().orElse(null);
        List<Process> preparations = processes.stream().filter(process -> process.getType() == ProcessType.SUBPROCESS).toList();
        List<Process> ordered = new ArrayList<>();
        if (main != null) ordered.add(main);
        ordered.addAll(preparations);
        Map<Long, Process> byId = new HashMap<>();
        processes.forEach(process -> byId.put(process.getId(), process));

        Set<Long> wholeUsedByMain = new HashSet<>();
        Set<String> stepsUsedByMain = new HashSet<>();
        if (main != null) {
            for (Process.ProcessNode node : stepsOf(main)) {
                for (Reference reference : referencesOf(fieldsOf(node))) {
                    if (reference.stepId() != null) stepsUsedByMain.add(reference.processId() + ":" + reference.stepId());
                    else wholeUsedByMain.add(reference.processId());
                }
            }
        }
        Set<Long> usedAnywhere = new HashSet<>();
        processes.forEach(process -> stepsOf(process).forEach(node ->
                referencesOf(fieldsOf(node)).forEach(reference -> usedAnywhere.add(reference.processId()))));

        if (main == null) {
            issues.add(new Issue("MAIN_MISSING", Severity.BLOCKER, null, null, "The recipe has no main process."));
        } else {
            if (stepsOf(main).isEmpty()) {
                issues.add(new Issue("MAIN_EMPTY", Severity.BLOCKER, main.getId(), null, "The main process has no steps yet."));
            }
            List<List<Process.ProcessNode>> stray = strayComponents(main);
            for (List<Process.ProcessNode> component : stray) {
                issues.add(new Issue("MAIN_DISCONNECTED", Severity.BLOCKER, main.getId(), component.get(0).getId(),
                        describe(main, component.get(0)) + " is not connected to the rest of the main process."));
            }
            List<Process.ProcessNode> starts = startNodesOf(main);
            if (stray.isEmpty() && starts.size() > 1) {
                issues.add(new Issue("MAIN_MULTIPLE_STARTS", Severity.WARNING, main.getId(), starts.get(1).getId(),
                        "The main process starts in " + starts.size() + " places."));
            }
        }

        for (Process extra : preparations.stream().skip(1).toList()) {
            issues.add(new Issue("PREP_MULTIPLE_PROCESSES", Severity.WARNING, extra.getId(), null,
                    "\"" + text(extra.getName()) + "\" is a separate preparation process; a recipe has one Preparation with several flows."));
        }

        for (Process preparation : preparations) {
            if (stepsOf(preparation).isEmpty()) {
                boolean referenced = usedAnywhere.contains(preparation.getId());
                issues.add(new Issue("PREP_EMPTY", referenced ? Severity.BLOCKER : Severity.WARNING, preparation.getId(), null,
                        "Preparation has no steps" + (referenced ? ", but a step uses something from it." : ".")));
                continue;
            }
            for (Flow flow : flowsOf(preparation)) {
                Process.ProcessNode anchor = flow.output() != null ? flow.output() : flow.nodes().get(0);
                String outputName = flow.output() == null ? "" : expectedOutputOf(flow.output());
                if (outputName.isEmpty()) {
                    issues.add(new Issue("PREP_OUTPUT_MISSING", Severity.BLOCKER, preparation.getId(), anchor.getId(),
                            "A preparation flow has no named output: give its last step an Expected Output."));
                }
                if (flow.endSteps().size() > 1) {
                    issues.add(new Issue("PREP_MULTIPLE_ENDS", Severity.WARNING, preparation.getId(), flow.endSteps().get(1).getId(),
                            "A preparation flow ends in " + flow.endSteps().size() + " steps."));
                }
                boolean used = wholeUsedByMain.contains(preparation.getId())
                        || flow.nodes().stream().anyMatch(node -> stepsUsedByMain.contains(preparation.getId() + ":" + node.getId()));
                if (main != null && !outputName.isEmpty() && !used) {
                    issues.add(new Issue("PREP_OUTPUT_UNUSED", Severity.WARNING, preparation.getId(), anchor.getId(),
                            "No main step uses \"" + outputName + "\" yet."));
                }
            }
        }

        Set<String> customIngredients = new HashSet<>();
        Set<String> customUnits = new HashSet<>();
        for (Process process : ordered) {
            Map<String, Process.ProcessNode> nodesById = nodesById(process);
            for (Process.ProcessNode node : stepsOf(process)) {
                Map<?, ?> fields = fieldsOf(node);
                String stepLabel = describe(process, node) + " in " + label(process);

                String action = text(fields.get("action"));
                if (action.isEmpty()) {
                    issues.add(new Issue("STEP_INCOMPLETE", Severity.BLOCKER, process.getId(), node.getId(), stepLabel + " has no action."));
                } else if (CUSTOM_ID.equals(action) && text(fields.get("customActionName")).isEmpty()) {
                    issues.add(new Issue("STEP_INCOMPLETE", Severity.BLOCKER, process.getId(), node.getId(), stepLabel + " uses a custom action without a name."));
                } else if (!CUSTOM_ID.equals(action) && !actionIds.contains(action)) {
                    issues.add(new Issue("STEP_INCOMPLETE", Severity.BLOCKER, process.getId(), node.getId(), stepLabel + " has an unknown action: " + action));
                } else if (process.getType() == ProcessType.MAIN && text(fields.get("actionDescription")).isEmpty()) {
                    issues.add(new Issue("STEP_INCOMPLETE", Severity.BLOCKER, process.getId(), node.getId(), stepLabel + " has no instruction."));
                }

                for (Reference reference : referencesOf(fields)) {
                    Process target = byId.get(reference.processId());
                    // A process can't use itself whole; a Preparation step using another of its steps is only a warning.
                    if (target == null || target.getType() != ProcessType.SUBPROCESS
                            || (Objects.equals(target.getId(), process.getId()) && reference.stepId() == null)) {
                        issues.add(new Issue("REF_DANGLING", Severity.BLOCKER, process.getId(), node.getId(),
                                stepLabel + " uses a prepared component that no longer exists."));
                        continue;
                    }
                    if (reference.stepId() != null) {
                        Process.ProcessNode source = nodesById(target).get(reference.stepId());
                        if (source == null || source.getKind() != ProcessNodeKind.STEP || expectedOutputOf(source).isEmpty()) {
                            issues.add(new Issue("REF_DANGLING", Severity.BLOCKER, process.getId(), node.getId(),
                                    stepLabel + " uses a prepared component whose preparation step is gone or has no Expected Output."));
                            continue;
                        }
                    }
                    if (process.getType() == ProcessType.SUBPROCESS) {
                        issues.add(new Issue("PREP_REFERENCES_PROCESS", Severity.WARNING, process.getId(), node.getId(),
                                stepLabel + " uses a prepared component; only main steps should."));
                    }
                }

                for (String stepId : stepRefsOf(fields)) {
                    Process.ProcessNode source = nodesById.get(stepId);
                    if (source == null || source.getKind() != ProcessNodeKind.STEP || source == node || expectedOutputOf(source).isEmpty()) {
                        issues.add(new Issue("REF_DANGLING", Severity.BLOCKER, process.getId(), node.getId(),
                                stepLabel + " uses the output of a step that no longer provides one."));
                    }
                }

                for (Map<?, ?> ingredient : ingredientsOf(fields)) {
                    if (CUSTOM_ID.equals(text(ingredient.get("ingredientId")))) {
                        String ingredientName = text(ingredient.get("customIngredientName"));
                        if (customIngredients.add(ingredientName.toLowerCase(Locale.ROOT))) {
                            issues.add(new Issue("CUSTOM_INGREDIENT", Severity.WARNING, process.getId(), node.getId(),
                                    "\"" + ingredientName + "\" isn't in the ingredient catalog."));
                        }
                    }
                    if (CUSTOM_ID.equals(text(ingredient.get("unit")))) {
                        String unitName = text(ingredient.get("customUnit"));
                        if (customUnits.add(unitName.toLowerCase(Locale.ROOT))) {
                            issues.add(new Issue("CUSTOM_UNIT", Severity.WARNING, process.getId(), node.getId(),
                                    "The unit \"" + unitName + "\" can't be converted for ordering."));
                        }
                    }
                }
            }
        }
        return new Readiness(List.copyOf(issues));
    }

    /** "The recipe isn't ready: <first blocker> (and N more)" — for an operation refused because of blockers. */
    public static String summarize(Readiness readiness) {
        List<Issue> blockers = readiness.blockers();
        if (blockers.isEmpty()) return "The recipe is ready.";
        return "The recipe isn't ready yet: " + blockers.get(0).message()
                + (blockers.size() > 1 ? " (and " + (blockers.size() - 1) + " more issue" + (blockers.size() > 2 ? "s" : "") + ")" : "");
    }

    // --- graph ---

    /** Weakly connected components, nodes in node order, components ordered by their first node. */
    private static List<List<Process.ProcessNode>> componentsOf(Process process) {
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

    /** Every component except the main one (the largest; the earliest on a tie). */
    private static List<List<Process.ProcessNode>> strayComponents(Process process) {
        List<List<Process.ProcessNode>> components = componentsOf(process);
        if (components.size() <= 1) return List.of();
        List<Process.ProcessNode> largest = components.get(0);
        for (List<Process.ProcessNode> component : components) {
            if (component.size() > largest.size()) largest = component;
        }
        List<Process.ProcessNode> mainComponent = largest;
        return components.stream().filter(component -> component != mainComponent).toList();
    }

    /** Each flow of a Preparation, with the STEPs it ends with and the step naming its output. */
    private static List<Flow> flowsOf(Process preparation) {
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

    private static List<Process.ProcessNode> startNodesOf(Process process) {
        Set<String> targets = new HashSet<>();
        for (Process.ProcessEdge edge : edgesOf(process)) {
            if (!Objects.equals(edge.getSource(), edge.getTarget())) targets.add(edge.getTarget());
        }
        return process.getNodes().stream().filter(Objects::nonNull).filter(node -> !targets.contains(node.getId())).toList();
    }

    private static List<Process.ProcessEdge> edgesOf(Process process) {
        return process.getEdges() == null ? List.of() : process.getEdges().stream().filter(Objects::nonNull).toList();
    }

    private static Map<String, Process.ProcessNode> nodesById(Process process) {
        Map<String, Process.ProcessNode> nodes = new LinkedHashMap<>();
        if (process.getNodes() != null) {
            process.getNodes().stream().filter(Objects::nonNull).forEach(node -> nodes.putIfAbsent(node.getId(), node));
        }
        return nodes;
    }

    // --- node data (the frontend's step shape: data.step, or flat on data in older data) ---

    private static List<Process.ProcessNode> stepsOf(Process process) {
        if (process.getNodes() == null) return List.of();
        return process.getNodes().stream().filter(node -> node != null && node.getKind() == ProcessNodeKind.STEP).toList();
    }

    private static Map<?, ?> fieldsOf(Process.ProcessNode node) {
        Map<String, Object> data = node.getData() == null ? Map.of() : node.getData();
        return data.get("step") instanceof Map<?, ?> step ? step : data;
    }

    private static String expectedOutputOf(Process.ProcessNode node) {
        return text(fieldsOf(node).get("expectedOutput"));
    }

    private static Map<?, ?> actionOnOf(Map<?, ?> fields) {
        return fields.get("actionOn") instanceof Map<?, ?> actionOn ? actionOn : Map.of();
    }

    private static List<Reference> referencesOf(Map<?, ?> fields) {
        List<Reference> references = new ArrayList<>();
        for (Object entry : listOf(actionOnOf(fields).get("processes"))) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            Long id = null;
            if (map.get("processId") instanceof Number number) {
                id = number.longValue();
            } else if (map.get("processId") instanceof String string && !string.isBlank()) {
                try {
                    id = Long.parseLong(string.trim());
                } catch (NumberFormatException ignored) {
                    // not a process id at all — nothing it could resolve to
                }
            }
            if (id == null) continue;
            String stepId = text(map.get("stepId"));
            references.add(new Reference(id, stepId.isEmpty() ? null : stepId));
        }
        return references;
    }

    private static List<String> stepRefsOf(Map<?, ?> fields) {
        List<String> ids = new ArrayList<>();
        for (Object entry : listOf(actionOnOf(fields).get("steps"))) {
            String id = entry instanceof Map<?, ?> map ? text(map.get("stepId")) : "";
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static List<Map<?, ?>> ingredientsOf(Map<?, ?> fields) {
        List<Map<?, ?>> ingredients = new ArrayList<>();
        for (Object entry : listOf(actionOnOf(fields).get("ingredients"))) {
            if (entry instanceof Map<?, ?> map) ingredients.add(map);
        }
        return ingredients;
    }

    private static List<?> listOf(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String text(Object value) {
        return value instanceof String string ? string.trim() : "";
    }

    private static String label(Process process) {
        return process.getType() == ProcessType.MAIN ? "the main process" : "Preparation";
    }

    private static String describe(Process process, Process.ProcessNode node) {
        String description = text(fieldsOf(node).get("actionDescription"));
        if (description.isEmpty() && node.getData() != null) description = text(node.getData().get("title"));
        if (!description.isEmpty()) return "\"" + (description.length() > 48 ? description.substring(0, 45) + "..." : description) + "\"";
        List<Process.ProcessNode> sameKind = process.getNodes().stream().filter(n -> n != null && n.getKind() == node.getKind()).toList();
        return (node.getKind() == ProcessNodeKind.CONDITION ? "check " : "step ") + (sameKind.indexOf(node) + 1);
    }
}
