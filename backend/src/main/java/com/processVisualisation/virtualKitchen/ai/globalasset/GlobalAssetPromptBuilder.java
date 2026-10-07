package com.processVisualisation.virtualKitchen.ai.globalasset;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds the image prompt for a global resource's reusable image.
 * <p>
 * Unlike step visualizations (whose prompt is written by the text model from the step's
 * context), this is a fixed template: every ingredient and every piece of equipment then shares
 * one consistent catalog look, no text-model call is paid per item, and no recipe context can leak
 * into an image that must work on any card (an onion, not an onion frying in a pan). The style
 * follows the step prompts' photorealistic single-still-frame convention.
 */
@Component
public class GlobalAssetPromptBuilder {

    private static final String SHARED_STYLE =
            " Centered as the only subject on a plain, light neutral background, soft even studio lighting,"
                    + " sharp focus, square framing, a single still frame."
                    + " No text, labels, logos, watermarks, hands or people; no cooking, no other food or props.";

    public String build(GlobalResource resource) {
        String subject = resource.name().trim() + describe(resource.description());
        return switch (resource.type()) {
            case INGREDIENT -> "Photorealistic studio photograph of " + subject
                    + ", fresh, raw and uncooked in its natural, whole, recognizable form,"
                    + " as shown on a grocery or recipe ingredient card." + SHARED_STYLE;
            case EQUIPMENT -> "Photorealistic product photograph of a " + subject
                    + ", a piece of kitchen equipment, clean, empty and unused,"
                    + " shown whole from a slight three-quarter angle as on a shop product card." + SHARED_STYLE;
        };
    }

    private static String describe(String description) {
        return StringUtils.hasText(description) ? " (" + description.trim() + ")" : "";
    }
}
