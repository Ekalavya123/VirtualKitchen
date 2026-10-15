package com.processVisualisation.virtualKitchen.ai.recipe.generation;

import com.processVisualisation.virtualKitchen.ai.recipe.generation.dto.GeneratedActionOnDTO;
import com.processVisualisation.virtualKitchen.ai.recipe.generation.dto.GeneratedActionOnIngredientDTO;
import com.processVisualisation.virtualKitchen.ai.recipe.generation.dto.GeneratedRecipeProcessDTO;
import com.processVisualisation.virtualKitchen.ai.recipe.generation.dto.GeneratedRecipeStepDTO;
import com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider;
import com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.ActionDefinition;
import com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FieldRequirement;
import com.processVisualisation.virtualKitchen.recipe.validation.ProcessValidationResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.CUSTOM_ID;
import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FIELD_DURATION;
import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FIELD_FLAME_LEVEL;
import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FIELD_PREPARATION_STYLE;
import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FIELD_QUANTITY;
import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FIELD_REPEAT_INTERVAL;
import static com.processVisualisation.virtualKitchen.ai.recipe.vocabulary.RecipeStepVocabularyProvider.FIELD_TEMPERATURE;

/**
 * Validates an AI-generated recipe structure — the MAIN cooking sequence plus zero or more independent
 * preparation flows (subprocesses whose name is their named output), see {@link GeneratedRecipeProcessDTO} —
 * before it is returned to the frontend. Reuses {@link ProcessValidationResult} — the
 * same simple valid/errors shape {@code ProcessValidator} already uses for
 * the persisted Process model — since this validates the same kind of
 * concern (structural integrity) one step earlier, before ids/edges exist.
 * <p>
 * Structure is strict: only MAIN steps may use a preparation's output, and every such reference must resolve;
 * preparations never reference each other (the model is flat). A STEP without any target is fine — it acts on
 * whatever is already in the pan. Vocabulary and per-action rules (allowed Action On targets, allowed fields,
 * allowed preparation styles) come from the shared catalog via
 * {@link RecipeStepVocabularyProvider} — see docs/recipe-vocabulary-v2.md, sections F and G. Most catalog slips
 * never reach this class: {@link RecipeProcessOutputNormalizer} repairs them first.
 * Error messages name the fix (allowed ids, or the canonical id an alias maps to) so the
 * single retry has what it needs.
 */
@Component
public class RecipeProcessGenerationValidator {

    private static final Set<String> NODE_TYPES = Set.of("STEP", "CONDITION");
    private static final Set<String> EXPECTED_RESULTS = Set.of("success", "failure");
    /** The Process model's pre-catalog unit enum (UnitType) — still accepted; the frontend maps these onto catalog units. */
    private static final Set<String> LEGACY_UNIT_IDS = Set.of("COUNT", "GRAM", "KG", "ML", "LITER");
    private static final Pattern DURATION_PATTERN = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]+)\\s*$");

    /** Generous but finite caps so a pathological AI response can't produce an unbounded/unusable recipe. */
    private static final int MAX_PREPARATIONS = 12;
    private static final int MAX_STEPS_PER_PROCESS = 40;

    private final RecipeStepVocabularyProvider vocabulary;

    public RecipeProcessGenerationValidator(RecipeStepVocabularyProvider recipeStepVocabularyProvider) {
        this.vocabulary = recipeStepVocabularyProvider;
    }

    /**
     * Validates the complete generation result, collecting every error found rather than failing
     * fast on the first one.
     *
     * @param mainProcess   the generated MAIN process (required)
     * @param preparations  the generated preparation flows (may be empty, never null)
     */
    public ProcessValidationResult validate(GeneratedRecipeProcessDTO mainProcess, List<GeneratedRecipeProcessDTO> preparations) {
        List<String> errors = new ArrayList<>();

        if (mainProcess == null) {
            errors.add("main is required");
            return new ProcessValidationResult(false, errors);
        }
        if (preparations == null) {
            errors.add("preparations must be an array");
            return new ProcessValidationResult(false, errors);
        }

        if (preparations.size() > MAX_PREPARATIONS) {
            errors.add("too many preparations generated (" + preparations.size() + " > " + MAX_PREPARATIONS + ")");
        }

        Set<String> declaredRefs = new HashSet<>();
        for (int i = 0; i < preparations.size(); i++) {
            GeneratedRecipeProcessDTO preparation = preparations.get(i);
            if (preparation == null) {
                errors.add("preparations[" + i + "] is null");
                continue;
            }
            String ref = preparation.getRef();
            if (isBlank(ref)) {
                errors.add("preparations[" + i + "].ref is required");
            } else if (!declaredRefs.add(ref)) {
                errors.add("duplicate preparation ref: " + ref);
            }
            if (isBlank(preparation.getName())) {
                errors.add("preparations[" + i + "] (ref=" + ref + ") is missing its output name");
            }
        }

        validateProcessSteps(mainProcess, "main", declaredRefs, errors);
        // Preparations are flat: none may use another preparation's output (null = no refs may be used at all).
        for (GeneratedRecipeProcessDTO preparation : preparations) {
            if (preparation == null || isBlank(preparation.getRef())) continue;
            validateProcessSteps(preparation, "preparations[" + preparation.getRef() + "]", null, errors);
        }

        return new ProcessValidationResult(errors.isEmpty(), errors);
    }

    /**
     * Problems that don't make the result unusable but deserve the user's attention: a preparation whose output no
     * main step uses (its ingredients still count, but the main process never says when it goes in).
     */
    public List<String> warnings(GeneratedRecipeProcessDTO mainProcess, List<GeneratedRecipeProcessDTO> preparations) {
        if (mainProcess == null || mainProcess.getSteps() == null || preparations == null) return List.of();
        Set<String> used = new HashSet<>();
        for (GeneratedRecipeStepDTO step : mainProcess.getSteps()) {
            if (step != null && step.getActionOn() != null && step.getActionOn().getProcesses() != null) {
                used.addAll(step.getActionOn().getProcesses());
            }
        }
        List<String> warnings = new ArrayList<>();
        for (GeneratedRecipeProcessDTO preparation : preparations) {
            if (preparation != null && !isBlank(preparation.getRef()) && !used.contains(preparation.getRef())) {
                warnings.add("The output of preparation '" + preparation.getName() + "' is never used by the main process");
            }
        }
        return warnings;
    }

    private void validateProcessSteps(
            GeneratedRecipeProcessDTO process, String label, Set<String> declaredRefs, List<String> errors
    ) {
        List<GeneratedRecipeStepDTO> steps = process.getSteps();
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

        validateStepList(steps, index -> label + ".steps[" + index + "]", declaredRefs, errors);
    }

    /**
     * Validates one ordered node list on its own, labelling each node by its stepId ("step s3") instead of its
     * position — so the labels stay stable when nodes are inserted or removed. Used by the AI edit flow to compare
     * a process before and after a set of edit operations; every node must carry a stepId.
     *
     * @param declaredRefs the process refs a step's {@code uses} may name
     */
    public List<String> validateSteps(List<GeneratedRecipeStepDTO> steps, Set<String> declaredRefs) {
        List<String> errors = new ArrayList<>();
        if (steps.size() > MAX_STEPS_PER_PROCESS) {
            errors.add("the process has too many steps (" + steps.size() + " > " + MAX_STEPS_PER_PROCESS + ")");
        }
        validateStepList(steps, index -> "step " + steps.get(index).getStepId(), declaredRefs, errors);
        return errors;
    }

    private void validateStepList(
            List<GeneratedRecipeStepDTO> steps, IntFunction<String> stepLabel, Set<String> declaredRefs, List<String> errors
    ) {
        // stepId -> position, collected up front so each reference can be checked for "earlier".
        Map<String, Integer> stepIndexById = new HashMap<>();
        for (int i = 0; i < steps.size(); i++) {
            GeneratedRecipeStepDTO step = steps.get(i);
            if (step == null || isBlank(step.getStepId())) continue;
            if (stepIndexById.putIfAbsent(step.getStepId(), i) != null) {
                errors.add(stepLabel.apply(i) + ".stepId is a duplicate within this process: " + step.getStepId());
            }
        }

        for (int i = 0; i < steps.size(); i++) {
            StepOutputScope scope = new StepOutputScope(steps, stepIndexById, i);
            validateStep(steps.get(i), stepLabel.apply(i), declaredRefs, scope, errors);
        }
    }

    /** The consuming step's position plus its process's steps — what a step-output reference is resolved against. */
    private record StepOutputScope(List<GeneratedRecipeStepDTO> steps, Map<String, Integer> stepIndexById, int consumerIndex) {}

    private void validateStep(
            GeneratedRecipeStepDTO step, String label, Set<String> declaredRefs, StepOutputScope scope, List<String> errors
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
            // Its "No" branch loops back to the step right before it (see the prompt's CONDITION BRANCHES).
            GeneratedRecipeStepDTO previous = scope.consumerIndex() > 0 ? scope.steps().get(scope.consumerIndex() - 1) : null;
            if (previous == null || !"STEP".equals(previous.getNodeType())) {
                errors.add(label + " is a CONDITION that must come immediately after the STEP it checks");
            }
            return;
        }

        // STEP
        String action = step.getAction();
        ActionDefinition definition = null;
        if (isBlank(action)) {
            errors.add(label + ".action is required for a STEP");
        } else {
            definition = vocabulary.action(action).orElse(null);
            if (definition == null) {
                errors.add(label + ".action is not in the known vocabulary: " + action + aliasHint(vocabulary.resolveActionId(action)));
            } else if (CUSTOM_ID.equals(action) && isBlank(step.getCustomActionName())) {
                errors.add(label + ".customActionName is required when action is custom");
            }
        }

        // actionOn is flattened onto the step in the model's output, so its errors are labelled at step level.
        validateActionOn(step.getActionOn(), definition, label, declaredRefs, scope, errors);
        if (definition != null) {
            validateStepFields(step, definition, label, errors);
        }
    }

    /** Step-level advanced fields: each is rejected when the action doesn't declare it, and format-checked when present. */
    private void validateStepFields(GeneratedRecipeStepDTO step, ActionDefinition action, String label, List<String> errors) {
        String flameLevel = step.getFlameLevel();
        if (!isBlank(flameLevel)) {
            if (!action.hasField(FIELD_FLAME_LEVEL)) {
                errors.add(notApplicable(label + ".flameLevel", action));
            } else if (!vocabulary.isFlameLevelId(flameLevel)) {
                errors.add(label + ".flameLevel must be one of " + vocabulary.flameLevelIdSet() + ", got: " + flameLevel);
            }
        }

        boolean hasTemperature = step.getTemperatureValue() != null || !isBlank(step.getTemperatureUnit()) || !isBlank(step.getTemperature());
        if (hasTemperature) {
            if (!action.hasField(FIELD_TEMPERATURE)) {
                errors.add(notApplicable(label + ".temperatureValue", action));
            } else if (step.getTemperatureValue() != null || !isBlank(step.getTemperatureUnit())) {
                if (step.getTemperatureValue() == null) {
                    errors.add(label + ".temperatureValue is required when temperatureUnit is set");
                } else if (!vocabulary.temperatureUnitIds().contains(step.getTemperatureUnit())) {
                    errors.add(label + ".temperatureUnit must be one of " + vocabulary.temperatureUnitIds() + " when temperatureValue is set, got: " + step.getTemperatureUnit());
                }
            }
        }

        validateDurationField(step.getDuration(), FIELD_DURATION, label + ".duration", action, errors);
        validateDurationField(step.getRepeatInterval(), FIELD_REPEAT_INTERVAL, label + ".repeatInterval", action, errors);
    }

    private void validateDurationField(String value, String fieldKey, String label, ActionDefinition action, List<String> errors) {
        if (isBlank(value)) return;
        if (!action.hasField(fieldKey)) {
            errors.add(notApplicable(label, action));
            return;
        }
        Matcher matcher = DURATION_PATTERN.matcher(value);
        if (!matcher.matches() || vocabulary.resolveDurationUnit(matcher.group(2)).isEmpty()) {
            errors.add(label + " must look like \"<number> <" + String.join("|", vocabulary.durationUnitIds()) + ">\", got: " + value);
        }
    }

    private void validateActionOn(
            GeneratedActionOnDTO actionOn, ActionDefinition action, String label, Set<String> declaredRefs,
            StepOutputScope scope, List<String> errors
    ) {
        List<GeneratedActionOnIngredientDTO> ingredients = actionOn == null || actionOn.getIngredients() == null ? List.of() : actionOn.getIngredients();
        List<String> processes = actionOn == null || actionOn.getProcesses() == null ? List.of() : actionOn.getProcesses();
        List<String> stepRefs = actionOn == null || actionOn.getSteps() == null ? List.of() : actionOn.getSteps();

        if (action != null) {
            if (!ingredients.isEmpty() && !action.actionOn().allowsIngredients()) {
                errors.add(label + ".ingredients: action " + action.id() + " cannot act on ingredients (allowed targets: " + action.actionOn() + ")");
            }
            if (!processes.isEmpty() && !action.actionOn().allowsProcesses()) {
                errors.add(label + ".uses: action " + action.id() + " cannot act on prepared components (allowed targets: " + action.actionOn() + ")");
            }
            // A step output is a prepared intermediate, like a subprocess output — same catalog rule.
            if (!stepRefs.isEmpty() && !action.actionOn().allowsProcesses()) {
                errors.add(label + ".fromSteps: action " + action.id() + " cannot act on step outputs (allowed targets: " + action.actionOn() + ")");
            }
            if (ingredients.size() > 1 && !action.multipleIngredients()) {
                errors.add(label + ".ingredients: action " + action.id() + " takes at most one ingredient per step, got " + ingredients.size());
            }
            // No target at all is fine: the step acts on what is already in the pan ("simmer", "transfer to a plate").
        }

        for (int i = 0; i < stepRefs.size(); i++) {
            validateStepOutputReference(stepRefs.get(i), scope, label + ".fromSteps[" + i + "]", errors);
        }

        for (int i = 0; i < ingredients.size(); i++) {
            validateActionOnIngredient(ingredients.get(i), action, label + ".ingredients[" + i + "]", errors);
        }

        for (int i = 0; i < processes.size(); i++) {
            String ref = processes.get(i);
            String itemLabel = label + ".uses[" + i + "]";
            if (isBlank(ref)) {
                errors.add(itemLabel + " is blank");
            } else if (declaredRefs == null) {
                errors.add(itemLabel + " uses " + ref + ", but only main steps may use a preparation's output"
                        + " — fold that preparation's steps into this one instead");
            } else if (!declaredRefs.contains(ref)) {
                errors.add(itemLabel + " references an unknown preparation ref: " + ref + " (declared: " + String.join(", ", declaredRefs) + ")");
            }
        }
    }

    /** Generated steps are an ordered list (connected linearly on conversion), so "earlier" is simply a lower index. */
    private void validateStepOutputReference(String stepId, StepOutputScope scope, String label, List<String> errors) {
        if (isBlank(stepId)) {
            errors.add(label + " is blank");
            return;
        }
        Integer sourceIndex = scope.stepIndexById().get(stepId);
        if (sourceIndex == null) {
            errors.add(label + " references an unknown stepId in this process: " + stepId);
            return;
        }
        if (sourceIndex == scope.consumerIndex()) {
            errors.add(label + " is a self-reference: " + stepId + " (a step cannot use its own output)");
            return;
        }
        if (sourceIndex > scope.consumerIndex()) {
            errors.add(label + " references a later step: " + stepId + " (only earlier steps' outputs can be used)");
            return;
        }
        GeneratedRecipeStepDTO source = scope.steps().get(sourceIndex);
        if (!"STEP".equals(source.getNodeType())) {
            errors.add(label + " references " + stepId + ", which is a " + source.getNodeType() + " — only a STEP's output can be used");
        } else if (isBlank(source.getExpectedOutput())) {
            errors.add(label + " references " + stepId + ", which has no expectedOutput to use");
        }
    }

    private void validateActionOnIngredient(GeneratedActionOnIngredientDTO ingredient, ActionDefinition action, String label, List<String> errors) {
        if (ingredient == null) {
            errors.add(label + " is null");
            return;
        }

        String ingredientId = ingredient.getIngredientId();
        boolean knownIngredient = false;
        if (isBlank(ingredientId)) {
            errors.add(label + ".ingredientId is required");
        } else if (vocabulary.ingredient(ingredientId).isEmpty()) {
            errors.add(label + ".ingredientId is not in the known catalog: " + ingredientId + aliasHint(vocabulary.resolveIngredientId(ingredientId)));
        } else if (CUSTOM_ID.equals(ingredientId) && isBlank(ingredient.getCustomIngredientName())) {
            errors.add(label + ".customIngredientName is required when ingredientId is custom");
        } else {
            knownIngredient = true;
        }

        String unit = ingredient.getUnit();
        boolean unitKnown = !isBlank(unit) && (vocabulary.unit(unit).isPresent() || LEGACY_UNIT_IDS.contains(unit));
        if (!isBlank(unit) && !unitKnown) {
            errors.add(label + ".unit must be a unit id from the catalog, got: " + unit + aliasHint(vocabulary.resolveUnitId(unit)));
        }
        boolean nonNumericUnit = unitKnown && !vocabulary.isQuantifiableUnit(unit);

        Double quantity = ingredient.getQuantity();
        if (quantity != null) {
            if (quantity < 0) {
                errors.add(label + ".quantity must not be negative");
            }
            if (nonNumericUnit) {
                errors.add(label + ".quantity must be null when unit is " + unit);
            } else if (isBlank(unit)) {
                errors.add(label + ".unit is required when quantity is set");
            }
        } else if (action != null && action.fieldRequirement(FIELD_QUANTITY) == FieldRequirement.REQUIRED && !nonNumericUnit) {
            errors.add(label + ".quantity is required for action " + action.id() + " (use unit to-taste/as-needed with a null quantity when there is no amount)");
        }

        validatePreparationStyle(ingredient.getPreparationStyle(), action, knownIngredient ? ingredientId : null, label, errors);
    }

    private void validatePreparationStyle(String preparationStyle, ActionDefinition action, String ingredientId, String label, List<String> errors) {
        if (isBlank(preparationStyle)) {
            // A required style is only enforced when this ingredient can take one of the action's styles at all
            // (e.g. cut + water has nothing to choose from).
            if (action != null && ingredientId != null
                    && action.fieldRequirement(FIELD_PREPARATION_STYLE) == FieldRequirement.REQUIRED
                    && !vocabulary.allowedPreparationStyles(action.id(), ingredientId).isEmpty()) {
                errors.add(label + ".preparationStyle is required for action " + action.id() + ", one of: "
                        + String.join("|", vocabulary.allowedPreparationStyles(action.id(), ingredientId)));
            }
            return;
        }
        if (!CUSTOM_ID.equals(preparationStyle) && !vocabulary.isPreparationStyleId(preparationStyle)) {
            errors.add(label + ".preparationStyle is not in the known vocabulary: " + preparationStyle + aliasHint(vocabulary.resolvePreparationStyleId(preparationStyle)));
            return;
        }
        if (action == null) return;
        if (!action.hasField(FIELD_PREPARATION_STYLE)) {
            errors.add(notApplicable(label + ".preparationStyle", action));
        } else if (!CUSTOM_ID.equals(preparationStyle) && !vocabulary.allowedPreparationStyles(action.id()).contains(preparationStyle)) {
            errors.add(label + ".preparationStyle " + preparationStyle + " is not allowed for action " + action.id() + ", use one of: "
                    + String.join("|", vocabulary.allowedPreparationStyles(action.id())));
        }
    }

    private static String notApplicable(String label, ActionDefinition action) {
        return label + " does not apply to action " + action.id() + " (allowed fields: " + String.join(", ", action.fields().keySet()) + ")";
    }

    /** Names the canonical id when an unknown value is one of the catalog's input aliases (e.g. "dhania" -> "cilantro"). */
    private static String aliasHint(Optional<String> canonical) {
        return canonical.map(id -> " (use the canonical id '" + id + "')").orElse("");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
