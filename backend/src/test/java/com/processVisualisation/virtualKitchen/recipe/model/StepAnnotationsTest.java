package com.processVisualisation.virtualKitchen.recipe.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.processVisualisation.virtualKitchen.recipe.model.StepAnnotations.Annotation;
import com.processVisualisation.virtualKitchen.recipe.model.StepAnnotations.Segment;
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

/** Runs the annotation cases shared with the frontend's tests/stepAnnotations.test.ts. */
class StepAnnotationsTest {

    @TestFactory
    Stream<DynamicTest> sharedCases() throws Exception {
        JsonNode cases;
        try (InputStream json = getClass().getResourceAsStream("/annotations/annotation-cases.json")) {
            cases = new ObjectMapper().readTree(json);
        }
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode testCase : cases) {
            tests.add(DynamicTest.dynamicTest(testCase.path("name").asText(), () -> {
                String text = testCase.path("text").asText();
                List<String> expected = new ArrayList<>();
                testCase.path("segments").forEach(segment -> expected.add(segment.has("text")
                        ? "text:" + segment.path("text").asText()
                        : segment.path("kind").asText() + "|" + segment.path("id").asText() + "|" + segment.path("label").asText()));

                List<Segment> segments = StepAnnotations.parse(text);
                assertEquals(expected, simplify(segments));
                assertEquals(testCase.path("plain").asText(), StepAnnotations.toPlainText(text));
                assertEquals(expected, simplify(StepAnnotations.parse(StepAnnotations.serialize(segments))));
            }));
        }
        return tests.stream();
    }

    @Test
    void remapsIdsAndUnwrapsTheOnesMappedToNull() {
        String text = "Add @[onions](ingredient:107) and @[sauce](prepared:12:a), then @[base](step:s1).";
        String remapped = StepAnnotations.remapIds(text, annotation -> switch (annotation.kind()) {
            case StepAnnotations.PREPARED -> "40:b";
            case StepAnnotations.STEP -> null;
            default -> annotation.id();
        });
        assertEquals("Add @[onions](ingredient:107) and @[sauce](prepared:40:b), then base.", remapped);
    }

    @Test
    void readsLiveLabelsWhenTheyResolve() {
        String text = "Add @[sauce](prepared:12:a) and @[salt](ingredient:33)";
        assertEquals("Add Tomato Sauce and salt",
                StepAnnotations.toPlainText(text, annotation -> annotation.kind().equals(StepAnnotations.PREPARED) ? "Tomato Sauce" : null));
    }

    @Test
    void textWithoutAnnotationsIsUnchanged() {
        assertEquals("Plain (text) [here]", StepAnnotations.remapIds("Plain (text) [here]", annotation -> "x"));
        assertFalse(StepAnnotations.hasAnnotations("Plain"));
        assertTrue(StepAnnotations.hasAnnotations("@[a](step:b)"));
        assertEquals("", StepAnnotations.toPlainText(null));
    }

    @Test
    void buildsTargetIds() {
        assertEquals("107", StepAnnotations.ingredientId("107", null));
        assertEquals("custom:Ghee", StepAnnotations.ingredientId("custom", " Ghee "));
        assertEquals("3", StepAnnotations.preparedId(3, null));
        assertEquals("3:abc", StepAnnotations.preparedId(3, "abc"));
        assertTrue(StepAnnotations.sameTarget(new Annotation("ingredient", "custom:ghee", "ghee"), "ingredient", "custom:Ghee"));
    }

    private static List<String> simplify(List<Segment> segments) {
        return segments.stream().map(segment -> segment.isAnnotation()
                ? segment.annotation().kind() + "|" + segment.annotation().id() + "|" + segment.annotation().label()
                : "text:" + segment.text()).toList();
    }
}
