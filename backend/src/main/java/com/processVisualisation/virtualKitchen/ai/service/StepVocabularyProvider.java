package com.processVisualisation.virtualKitchen.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.stream.StreamSupport;

/**
 * Loads the recipe step-property vocabulary (actions, ingredients, units, preparation styles,
 * flame levels) from {@code stepCatalogs.data.json} — the same file the frontend's flow-editor
 * catalogs are built from (copied onto the classpath at build time, see backend/pom.xml) — so the
 * AI prompt vocabulary in {@link AIRecipeFlowPromptBuilder} can never drift out of sync with the
 * frontend's actual catalog again.
 */
@Component
public class StepVocabularyProvider {

    private static final String CATALOG_RESOURCE = "stepCatalogs.data.json";

    private final String actionIds;
    private final String ingredientIds;
    private final String unitIds;
    private final String preparationStyleIds;
    private final String flameLevelIds;

    public StepVocabularyProvider() {
        JsonNode root = loadCatalog();
        this.actionIds = joinIds(root, "actions");
        this.ingredientIds = joinIds(root, "ingredients");
        this.unitIds = joinIds(root, "units");
        this.preparationStyleIds = joinIds(root, "preparationStyles");
        this.flameLevelIds = joinIds(root, "flameLevels");
    }

    private JsonNode loadCatalog() {
        try (InputStream stream = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            return new ObjectMapper().readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + CATALOG_RESOURCE + " from the classpath", e);
        }
    }

    private String joinIds(JsonNode root, String arrayField) {
        return StreamSupport.stream(root.path(arrayField).spliterator(), false)
                .map(entry -> entry.path("id").asText())
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    public String actionIds() {
        return actionIds;
    }

    public String ingredientIds() {
        return ingredientIds;
    }

    public String unitIds() {
        return unitIds;
    }

    public String preparationStyleIds() {
        return preparationStyleIds;
    }

    public String flameLevelIds() {
        return flameLevelIds;
    }
}
