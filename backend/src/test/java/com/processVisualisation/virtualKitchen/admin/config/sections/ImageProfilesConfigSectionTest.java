package com.processVisualisation.virtualKitchen.admin.config.sections;

import com.processVisualisation.virtualKitchen.admin.config.ConfigField;
import com.processVisualisation.virtualKitchen.ai.provider.ImageStorageClient;
import com.processVisualisation.virtualKitchen.ai.recipe.visualization.RecipeProcessVisualizationService;
import com.processVisualisation.virtualKitchen.ai.recipe.visualization.VisualizationImageProperties;
import com.processVisualisation.virtualKitchen.ai.recipe.visualization.VisualizationSceneAnchorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ImageProfilesConfigSectionTest {

    private VisualizationSceneAnchorService sceneAnchors;
    private ImageProfilesConfigSection section;

    @BeforeEach
    void setUp() {
        sceneAnchors = new VisualizationSceneAnchorService(
                mock(RecipeProcessVisualizationService.class), mock(ImageStorageClient.class), true);
        section = new ImageProfilesConfigSection(new VisualizationImageProperties(), sceneAnchors);
    }

    @Test
    void sceneAnchorToggle_showsTheConfiguredValue() {
        ConfigField field = field(Map.of());

        assertEquals(ConfigField.Type.TOGGLE, field.type());
        assertEquals("true", field.baseline());
        assertEquals("true", field.current());
        assertFalse(field.overridden());
    }

    @Test
    void overridingTheSceneAnchor_switchesTheLiveService_andRemovingTheOverrideRestoresIt() {
        section.apply(Map.of(ImageProfilesConfigSection.SCENE_ANCHOR_ENABLED, "false"));
        assertFalse(sceneAnchors.isEnabled());
        ConfigField field = field(Map.of(ImageProfilesConfigSection.SCENE_ANCHOR_ENABLED, "false"));
        assertEquals("false", field.current());
        assertTrue(field.overridden());

        section.apply(Map.of());
        assertTrue(sceneAnchors.isEnabled(), "no override = back to ai.visualization.scene-anchor.enabled");
    }

    @Test
    void anUnknownKey_isRejected_andLeavesTheSceneAnchorAlone() {
        assertThrows(IllegalArgumentException.class, () -> section.apply(Map.of(
                ImageProfilesConfigSection.SCENE_ANCHOR_ENABLED, "false", "nope", "1")));
        assertTrue(sceneAnchors.isEnabled());
    }

    private ConfigField field(Map<String, String> overrides) {
        return section.fields(overrides).stream()
                .filter(f -> f.key().equals(ImageProfilesConfigSection.SCENE_ANCHOR_ENABLED))
                .findFirst().orElseThrow();
    }
}
