package com.processVisualisation.virtualKitchen.admin.config.sections;

import com.processVisualisation.virtualKitchen.admin.config.ConfigField;
import com.processVisualisation.virtualKitchen.admin.config.ConfigSection;
import com.processVisualisation.virtualKitchen.ai.recipe.visualization.VisualizationImageProperties;
import com.processVisualisation.virtualKitchen.ai.recipe.visualization.VisualizationImageProperties.Profile;
import com.processVisualisation.virtualKitchen.ai.recipe.visualization.VisualizationSceneAnchorService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Step image settings:
 * <ul>
 *   <li>the scene anchor switch ({@code ai.visualization.scene-anchor.enabled}), applied to
 *       {@link VisualizationSceneAnchorService} and read by every new visuals job;</li>
 *   <li>image profiles: width, height and steps for each image purpose, plus the default profile. Applied to
 *       {@link VisualizationImageProperties}, which {@code VisualizationImageProfiles} reads for every image.</li>
 * </ul>
 * Images already generated are unchanged.
 */
@Component
@Order(40)
public class ImageProfilesConfigSection implements ConfigSection {

    private static final List<String> SIZES = Fields.values("", 256, 384, 512, 768, 1024);
    private static final List<String> STEPS = Fields.values("", 4, 8, 12, 20, 30);
    private static final String DEFAULT = "default";

    private static final Map<String, String> PURPOSES = new LinkedHashMap<>();

    static {
        PURPOSES.put(DEFAULT, "Default (any purpose not set below)");
        PURPOSES.put(VisualizationImageProperties.INGREDIENT_PREPARATION, "Ingredient preparation");
        PURPOSES.put(VisualizationImageProperties.INGREDIENT_ADDITION, "Ingredient addition");
        PURPOSES.put(VisualizationImageProperties.COOKING_PROCESS, "Cooking");
        PURPOSES.put(VisualizationImageProperties.MIXING, "Mixing");
        PURPOSES.put(VisualizationImageProperties.WAITING_RESTING, "Waiting & resting");
        PURPOSES.put(VisualizationImageProperties.SERVING, "Serving");
    }

    static final String SCENE_ANCHOR_ENABLED = "sceneAnchor.enabled";

    private final VisualizationImageProperties properties;
    private final VisualizationSceneAnchorService sceneAnchorService;
    private final Map<String, Profile> baseline = new LinkedHashMap<>();

    public ImageProfilesConfigSection(VisualizationImageProperties properties,
                                      VisualizationSceneAnchorService sceneAnchorService) {
        this.properties = properties;
        this.sceneAnchorService = sceneAnchorService;
        PURPOSES.keySet().forEach(purpose -> baseline.put(purpose, copy(profile(purpose))));
    }

    @Override
    public String id() {
        return "images";
    }

    @Override
    public String label() {
        return "Images";
    }

    @Override
    public String description() {
        return "How new step images are made: the scene anchor, and the size and steps per kind of step. Empty "
                + "sizes use the default profile, then the provider's own setting. Bigger images cost more time and money.";
    }

    @Override
    public List<ConfigField> fields(Map<String, String> overrides) {
        List<ConfigField> fields = new ArrayList<>();
        fields.add(Fields.toggle(SCENE_ANCHOR_ENABLED, "Use a scene anchor", "Scene anchor",
                sceneAnchorService.isBaselineEnabled(), overrides,
                "One empty-scene image per recipe that every step image is drawn against, for a consistent pan, "
                        + "counter and lighting. Costs one extra image per recipe. Only image models that take a "
                        + "reference image (Gemini) use the image; others get the dish context line. Applies to the "
                        + "next visuals job."));
        baseline.forEach((purpose, base) -> {
            String group = PURPOSES.get(purpose);
            fields.add(Fields.select(key(purpose, "width"), "Width (px)", group, SIZES, base.getWidth(), overrides, null));
            fields.add(Fields.select(key(purpose, "height"), "Height (px)", group, SIZES, base.getHeight(), overrides, null));
            fields.add(Fields.select(key(purpose, "steps"), "Steps", group, STEPS, base.getSteps(), overrides,
                    "Only providers with a steps setting use it."));
        });
        return fields;
    }

    @Override
    public void apply(Map<String, String> overrides) {
        Fields.requireKnown(overrides, fields(overrides));
        Map<String, Profile> next = new LinkedHashMap<>();
        baseline.forEach((purpose, base) -> {
            Profile profile = new Profile();
            profile.setWidth(Fields.optionalInt(overrides, key(purpose, "width"), base.getWidth()));
            profile.setHeight(Fields.optionalInt(overrides, key(purpose, "height"), base.getHeight()));
            profile.setSteps(Fields.optionalInt(overrides, key(purpose, "steps"), base.getSteps()));
            next.put(purpose, profile);
        });
        boolean sceneAnchorEnabled = overrides.containsKey(SCENE_ANCHOR_ENABLED)
                ? Boolean.parseBoolean(overrides.get(SCENE_ANCHOR_ENABLED))
                : sceneAnchorService.isBaselineEnabled();

        // Everything is parsed above, so nothing below can fail halfway through.
        sceneAnchorService.setEnabled(sceneAnchorEnabled);
        properties.setDefaultProfile(next.remove(DEFAULT));
        Map<String, Profile> profiles = new LinkedHashMap<>(properties.getProfiles());
        next.forEach((purpose, profile) -> {
            boolean empty = profile.getWidth() == null && profile.getHeight() == null && profile.getSteps() == null;
            if (empty) {
                profiles.remove(purpose);
            } else {
                profiles.put(purpose, profile);
            }
        });
        properties.setProfiles(profiles);
    }

    private Profile profile(String purpose) {
        if (DEFAULT.equals(purpose)) {
            return properties.getDefaultProfile();
        }
        return properties.getProfiles().get(purpose);
    }

    private static Profile copy(Profile profile) {
        Profile copy = new Profile();
        if (profile != null) {
            copy.setWidth(profile.getWidth());
            copy.setHeight(profile.getHeight());
            copy.setSteps(profile.getSteps());
        }
        return copy;
    }

    private static String key(String purpose, String property) {
        return "profile." + purpose + "." + property;
    }
}
