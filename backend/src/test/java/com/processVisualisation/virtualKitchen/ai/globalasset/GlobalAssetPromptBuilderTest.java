package com.processVisualisation.virtualKitchen.ai.globalasset;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalAssetPromptBuilderTest {

    private final GlobalAssetPromptBuilder builder = new GlobalAssetPromptBuilder();

    @Test
    void ingredientPromptIsARecipeIndependentCatalogShot() {
        String prompt = builder.build(new GlobalResource(GlobalResourceType.INGREDIENT, 1L, "Green Chili", "fresh green chili pepper", null));

        assertThat(prompt)
                .contains("Green Chili (fresh green chili pepper)")
                .contains("uncooked")
                .contains("plain, light neutral background")
                .contains("no cooking")
                .doesNotContainIgnoringCase("pan with")
                .doesNotContainIgnoringCase("recipe step");
    }

    @Test
    void equipmentPromptDescribesAnEmptyProduct() {
        String prompt = builder.build(new GlobalResource(GlobalResourceType.EQUIPMENT, 2L, "Kettle", null, null));

        assertThat(prompt)
                .startsWith("Photorealistic product photograph of a Kettle,")
                .contains("clean, empty and unused")
                .doesNotContain("()");
    }

    @Test
    void ingredientAndEquipmentPromptsDiffer() {
        String ingredient = builder.build(new GlobalResource(GlobalResourceType.INGREDIENT, 1L, "Onion", null, null));
        String equipment = builder.build(new GlobalResource(GlobalResourceType.EQUIPMENT, 1L, "Onion", null, null));

        assertThat(ingredient).isNotEqualTo(equipment);
    }
}
