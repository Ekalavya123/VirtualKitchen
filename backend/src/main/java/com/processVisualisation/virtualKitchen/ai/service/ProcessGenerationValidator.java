package com.processVisualisation.virtualKitchen.ai.service;

import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedActionOnDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedActionOnIngredientDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedProcessDTO;
import com.processVisualisation.virtualKitchen.recipe.dto.GeneratedProcessStepDTO;
import com.processVisualisation.virtualKitchen.recipe.validation.ProcessValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates an AI-generated semantic Process structure (a MAIN process plus
 * zero or more subprocesses, see {@link GeneratedProcessDTO}) before it is
 * returned to the frontend. Reuses {@link ProcessValidationResult} — the
 * same simple valid/errors shape {@code ProcessValidator} already uses for
 * the persisted Process model — since this validates the same kind of
 * concern (structural integrity) one step earlier, before ids/edges exist.
 */
@Component
public class ProcessGenerationValidator {

    private static final Set<String> NODE_TYPES = Set.of("STEP", "CONDITION");
    private static final Set<String> EXPECTED_RESULTS = Set.of("success", "failure");
    private static final Set<String> UNIT_IDS = Set.of("COUNT", "GRAM", "KG", "ML", "LITER");
    private static final String CUSTOM_ID = "custom";

    /** Generous but finite caps so a pathological AI response can't produce an unbounded/unusable recipe. */
    private static final int MAX_SUBPROCESSES = 12;
    private static final int MAX_STEPS_PER_PROCESS = 40;

    private final Set<String> actionIds;
    private final Set<String> ingredientIds;
    private final Set<String> preparationStyleIds;
    private final Set<String> flameLevelIds;

    public ProcessGenerationValidator(StepVocabularyProvider stepVocabularyProvider) {
        this.actionIds = toSet(stepVocabularyProvider.actionIds());
        this.ingredientIds = toSet(stepVocabularyProvider.ingredientIds());
        this.preparationStyleIds = toSet(stepVocabularyProvider.preparationStyleIds());
        this.flameLevelIds = toSet(stepVocabularyProvider.flameLevelIds());
    }

    private static Set<String> toSet(String pipeJoined) {
        Set<String> set = new HashSet<>();
        if (pipeJoined != null && !pipeJoined.isBlank()) {
            for (String id : pipeJoined.split("\\|")) {
                set.add(id.trim());
            }
        }
        return set;
    }

    /**
     * Validates the complete generation result, collecting every error found rather than failing
     * fast on the first one.
     *
     * @param mainProcess   the generated MAIN process (required)
     * @param subprocesses  the generated subprocesses (may be empty, never null)
     */
    public ProcessValidationResult validate(GeneratedProcessDTO mainProcess, List<GeneratedProcessDTO> subprocesses) {
        List<String> errors = new ArrayList<>();

        if (mainProcess == null) {
            errors.add("mainProcess is required");
            return new ProcessValidationResult(false, errors);
        }
        if (subprocesses == null) {
            errors.add("subprocesses must be an array");
            return new ProcessValidationResult(false, errors);
        }

        if (subprocesses.size() > MAX_SUBPROCESSES) {
            errors.add("too many subprocesses generated (" + subprocesses.size() + " > " + MAX_SUBPROCESSES + ")");
        }

        Set<String> declaredRefs = new HashSet<>();
        Map<String, GeneratedProcessDTO> subprocessByRef = new HashMap<>();
        for (int i = 0; i < subprocesses.size(); i++) {
            GeneratedProcessDTO subprocess = subprocesses.get(i);
            if (subprocess == null) {
                errors.add("subprocesses[" + i + "] is null");
                continue;
            }
            String ref = subprocess.getRef();
            if (isBlank(ref)) {
                errors.add("subprocesses[" + i + "].ref is required");
            } else if (!declaredRefs.add(ref)) {
                errors.add("duplicate subprocess ref: " + ref);
            } else {
                subprocessByRef.put(ref, subprocess);
            }
            if (isBlank(subprocess.getName())) {
                errors.add("subprocesses[" + i + "] (ref=" + ref + ") is missing a name");
            }
        }

        validateProcessSteps(mainProcess, "mainProcess", declaredRefs, null, errors);
        for (GeneratedProcessDTO subprocess : subprocesses) {
            if (subprocess == null || isBlank(subprocess.getRef())) continue;
            validateProcessSteps(subprocess, "subprocess[" + subprocess.getRef() + "]", declaredRefs, subprocess.getRef(), errors);
        }

        validateNoCircularSubprocessReferences(subprocessByRef, errors);

        return new ProcessValidationResult(errors.isEmpty(), errors);
    }

    private void validateProcessSteps(
            GeneratedProcessDTO process, String label, Set<String> declaredRefs, String ownRef, List<String> errors
    ) {
        List<GeneratedProcessStepDTO> steps = process.getSteps();
        if (steps == null) {
            errors.add(label + ".steps must be an array");
            return;
        }
        if (steps.isEmpty()) {
            errors.add(label + " has no steps");
            return;
        }
        if (steps.size() > MAX_STEPS_PER_PROCESS) {
            errors.add(label + " has too many steps (" + steps.size() + " > " + MAX_STEPS_PER_PROCESS + ")");
        }

        for (int i = 0; i < steps.size(); i++) {
            validateStep(steps.get(i), label + ".steps[" + i + "]", declaredRefs, ownRef, errors);
        }
    }

    private void validateStep(
            GeneratedProcessStepDTO step, String label, Set<String> declaredRefs, String ownRef, List<String> errors
    ) {
        if (step == null) {
            errors.add(label + " is null");
            return;
        }

        String nodeType = step.getNodeType();
        if (isBlank(nodeType)) {
            errors.add(label + ".nodeType is required");
            return;
        }
        if (!NODE_TYPES.contains(nodeType)) {
            errors.add(label + ".nodeType must be STEP or CONDITION, got: " + nodeType);
            return;
        }

        if (isBlank(step.getActionDescription())) {
            errors.add(label + ".actionDescription is required");
        }
        if (step.getExpectedOutput() == null) {
            errors.add(label + ".expectedOutput is required (may be an empty string)");
        }

        if ("CONDITION".equals(nodeType)) {
            if (isBlank(step.getTitle())) {
                errors.add(label + ".title is required for a CONDITION step");
            }
            String expectedResult = step.getExpectedResult();
            if (!isBlank(expectedResult) && !EXPECTED_RESULTS.contains(expectedResult.toLowerCase())) {
                errors.add(label + ".expectedResult must be success or failure, got: " + expectedResult);
            }
            return;
        }

        // STEP
        String action = step.getAction();
        if (isBlank(action)) {
            errors.add(label + ".action is required for a STEP");
        } else if (!actionIds.contains(action) && !CUSTOM_ID.equals(action)) {
            errors.add(label + ".action is not in the known vocabulary: " + action);
        }

        validateActionOn(step.getActionOn(), label + ".actionOn", declaredRefs, ownRef, errors);
    }

    private void validateActionOn(
            GeneratedActionOnDTO actionOn, String label, Set<String> declaredRefs, String ownRef, List<String> errors
    ) {
        if (actionOn == null) {
            // Action On is allowed to be entirely absent (a step with neither ingredients nor a
            // subprocess reference) — nothing further to check.
            return;
        }

        List<GeneratedActionOnIngredientDTO> ingredients = actionOn.getIngredients();
        if (ingredients != null) {
            for (int i = 0; i < ingredients.size(); i++) {
                validateActionOnIngredient(ingredients.get(i), label + ".ingredients[" + i + "]", errors);
            }
        }

        List<String> processes = actionOn.getProcesses();
        if (processes == null) return;
        for (int i = 0; i < processes.size(); i++) {
            String ref = processes.get(i);
            String itemLabel = label + ".processes[" + i + "]";
            if (isBlank(ref)) {
                errors.add(itemLabel + " is blank");
                continue;
            }
            if (ownRef != null && ownRef.equals(ref)) {
                errors.add(itemLabel + " is a self-reference: " + ref);
                continue;
            }
            if (!declaredRefs.contains(ref)) {
                errors.add(itemLabel + " references an unknown subprocess ref: " + ref);
            }
        }
    }

    private void validateActionOnIngredient(GeneratedActionOnIngredientDTO ingredient, String label, List<String> errors) {
        if (ingredient == null) {
            errors.add(label + " is null");
            return;
        }

        String ingredientId = ingredient.getIngredientId();
        if (isBlank(ingredientId)) {
            errors.add(label + ".ingredientId is required");
        } else if (!ingredientIds.contains(ingredientId) && !CUSTOM_ID.equals(ingredientId)) {
            errors.add(label + ".ingredientId is not in the known catalog: " + ingredientId);
        } else if (CUSTOM_ID.equals(ingredientId) && isBlank(ingredient.getCustomIngredientName())) {
            errors.add(label + ".customIngredientName is required when ingredientId is custom");
        }

        if (ingredient.getQuantity() == null) {
            errors.add(label + ".quantity is required");
        } else if (ingredient.getQuantity() < 0) {
            errors.add(label + ".quantity must not be negative");
        }

        String unit = ingredient.getUnit();
        if (isBlank(unit)) {
            errors.add(label + ".unit is required");
        } else if (!UNIT_IDS.contains(unit)) {
            errors.add(label + ".unit must be one of " + UNIT_IDS + ", got: " + unit);
        }

        String preparationStyle = ingredient.getPreparationStyle();
        if (!isBlank(preparationStyle) && !preparationStyleIds.contains(preparationStyle) && !CUSTOM_ID.equals(preparationStyle)) {
            errors.add(label + ".preparationStyle is not in the known vocabulary: " + preparationStyle);
        }
    }

    /**
     * Detects cycles among subprocess-to-subprocess Action On references (e.g. A references B, B
     * references A) via a plain depth-first search over the ref graph. Self-references are already
     * rejected in {@link #validateActionOn}, so this only needs to catch cycles of length &gt;= 2.
     */
    private void validateNoCircularSubprocessReferences(Map<String, GeneratedProcessDTO> subprocessByRef, List<String> errors) {
        Map<String, Set<String>> referencesByRef = new HashMap<>();
        for (Map.Entry<String, GeneratedProcessDTO> entry : subprocessByRef.entrySet()) {
            referencesByRef.put(entry.getKey(), collectReferencedSubprocesses(entry.getValue()));
        }

        Set<String> reportedCycles = new HashSet<>();
        for (String start : referencesByRef.keySet()) {
            List<String> cycle = findCycleFrom(start, referencesByRef);
            if (cycle != null) {
                String key = new HashSet<>(cycle).toString();
                if (reportedCycles.add(key)) {
                    errors.add("circular subprocess reference detected: " + String.join(" -> ", cycle));
                }
            }
        }
    }

    private Set<String> collectReferencedSubprocesses(GeneratedProcessDTO subprocess) {
        Set<String> refs = new HashSet<>();
        if (subprocess.getSteps() == null) return refs;
        for (GeneratedProcessStepDTO step : subprocess.getSteps()) {
            if (step == null || step.getActionOn() == null || step.getActionOn().getProcesses() == null) continue;
            for (String ref : step.getActionOn().getProcesses()) {
                if (!isBlank(ref)) refs.add(ref);
            }
        }
        return refs;
    }

    private List<String> findCycleFrom(String start, Map<String, Set<String>> referencesByRef) {
        Deque<String> path = new ArrayDeque<>();
        Set<String> onPath = new HashSet<>();
        return dfs(start, referencesByRef, path, onPath);
    }

    private List<String> dfs(String current, Map<String, Set<String>> referencesByRef, Deque<String> path, Set<String> onPath) {
        path.addLast(current);
        onPath.add(current);

        for (String next : referencesByRef.getOrDefault(current, Set.of())) {
            if (onPath.contains(next)) {
                path.addLast(next);
                return new ArrayList<>(path);
            }
            List<String> found = dfs(next, referencesByRef, path, onPath);
            if (found != null) return found;
        }

        path.removeLast();
        onPath.remove(current);
        return null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
