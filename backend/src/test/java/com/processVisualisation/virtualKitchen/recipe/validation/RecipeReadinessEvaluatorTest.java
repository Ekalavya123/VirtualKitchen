package com.processVisualisation.virtualKitchen.recipe.validation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.recipe.validation.RecipeReadinessEvaluator.Issue;
import com.processVisualisation.virtualKitchen.recipe.validation.RecipeReadinessEvaluator.Readiness;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the readiness cases shared with the frontend's tests/recipeReadiness.test.ts, so the backend gate and the
 * editor's live warnings can never disagree about what blocks a whole-recipe operation.
 */
class RecipeReadinessEvaluatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final RecipeReadinessEvaluator evaluator = new RecipeReadinessEvaluator();

    @TestFactory
    Stream<DynamicTest> sharedCases() throws Exception {
        JsonNode cases;
        try (InputStream json = getClass().getResourceAsStream("/readiness/readiness-cases.json")) {
            cases = objectMapper.readTree(json);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : cases) {
            tests.add(DynamicTest.dynamicTest(testCase.path("name").asText(), () -> {
                List<Process> processes = new ArrayList<>();
                for (JsonNode process : testCase.path("processes")) {
                    processes.add(objectMapper.treeToValue(process, Process.class));
                }
                Readiness readiness = evaluator.evaluate(processes);

                List<String> expected = new ArrayList<>();
                testCase.path("expected").forEach(issue -> expected.add(String.join("|",
                        issue.path("code").asText(), issue.path("severity").asText(),
                        issue.path("processId").isNull() ? "null" : issue.path("processId").asText(),
                        issue.path("nodeId").isNull() ? "null" : issue.path("nodeId").asText())));
                List<String> actual = readiness.issues().stream().map(RecipeReadinessEvaluatorTest::key).toList();

                assertEquals(expected.stream().sorted().toList(), actual.stream().sorted().toList());
                boolean hasBlocker = expected.stream().anyMatch(key -> key.contains("|BLOCKER|"));
                assertEquals(!hasBlocker, readiness.ready());
            }));
        }
        return tests.stream();
    }

    @Test
    void summarizeNamesTheFirstBlockerAndCountsTheRest() {
        Readiness readiness = new Readiness(List.of(
                new Issue("MAIN_EMPTY", RecipeReadinessEvaluator.Severity.BLOCKER, 1L, null, "The main process has no steps yet."),
                new Issue("PREP_EMPTY", RecipeReadinessEvaluator.Severity.BLOCKER, 2L, null, "x"),
                new Issue("PREP_OUTPUT_UNUSED", RecipeReadinessEvaluator.Severity.WARNING, 2L, null, "y")));

        assertFalse(readiness.ready());
        String summary = RecipeReadinessEvaluator.summarize(readiness);
        assertTrue(summary.contains("The main process has no steps yet."), summary);
        assertTrue(summary.contains("(and 1 more issue)"), summary);
    }

    private static String key(Issue issue) {
        return String.join("|", issue.code(), issue.severity().name(), String.valueOf(issue.processId()), String.valueOf(issue.nodeId()));
    }
}
