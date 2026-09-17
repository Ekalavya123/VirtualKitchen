package com.processVisualisation.virtualKitchen.service;

import com.processVisualisation.virtualKitchen.ai.artifact.AiArtifactKeyBuilder;
import com.processVisualisation.virtualKitchen.ai.registry.AiCapability;
import com.processVisualisation.virtualKitchen.common.utils.VisualizationKeyBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiArtifactKeyBuilderTest {

    private static final String TYPE = "visualization-image";

    @Test
    void build_sameInputs_producesSameKey() {
        String first = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a knife");
        String second = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a knife");

        assertEquals(first, second, "an identical retry must reuse the stored payload");
    }

    @Test
    void build_changedPrompt_producesDifferentKey() {
        String original = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a knife");
        String edited = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a spoon");

        assertNotEquals(original, edited, "a changed prompt must invalidate the stored payload");
    }

    /**
     * The regression guard for the collision described in {@link VisualizationKeyBuilder}'s javadoc:
     * a purely content-derived key let two unrelated sparse steps share one image. Because the hash
     * here is only ever a suffix beneath the correlation scope, two steps with identical — or
     * entirely blank — prompts must still land on different keys.
     */
    @Test
    void build_blankPromptInDifferentScopes_producesDifferentKeys() {
        String stepOne = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE,
                VisualizationKeyBuilder.build("recipe-1", "step-1"), "");
        String stepTwo = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE,
                VisualizationKeyBuilder.build("recipe-2", "step-9"), "");

        assertNotEquals(stepOne, stepTwo, "blank prompts must never make two steps share a payload");
    }

    @Test
    void build_nullPrompt_isTreatedAsBlankRatherThanFailing() {
        String withNull = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", (String) null);
        String withBlank = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "");

        assertEquals(withBlank, withNull);
    }

    @Test
    void build_differentCapability_producesDifferentKey() {
        String image = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a knife");
        String text = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_TEXT, TYPE, "recipe-1::step-1", "a knife");

        assertNotEquals(image, text);
    }

    @Test
    void build_keyCarriesScopeButNotTheModel() {
        String key = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a knife");

        assertTrue(key.startsWith("TEXT_TO_IMAGE::" + TYPE + "::recipe-1::step-1::"),
                "scope must precede the fingerprint so it can never be collapsed away");
        assertFalse(key.contains("gemini"), "the producing model must not take part in the key");
    }

    @Test
    void build_inputBoundariesCannotBeAmbiguous() {
        String split = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "ab", "c");
        String joined = AiArtifactKeyBuilder.build(AiCapability.TEXT_TO_IMAGE, TYPE, "recipe-1::step-1", "a", "bc");

        assertNotEquals(split, joined, "inputs must be delimited before hashing");
    }
}
