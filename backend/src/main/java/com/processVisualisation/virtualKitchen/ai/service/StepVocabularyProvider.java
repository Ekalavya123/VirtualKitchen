package com.processVisualisation.virtualKitchen.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
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

    private final Map<String, String> actionLabels;
    private final Map<String, String> ingredientLabels;
    private final Map<String, String> preparationStyleLabels;
    private final Map<String, String> flameLevelLabels;

    public StepVocabularyProvider() {
        JsonNode root = loadCatalog();
        this.actionIds = joinIds(root, "actions");
        this.ingredientIds = joinIds(root, "ingredients");
        this.unitIds = joinIds(root, "units");
        this.preparationStyleIds = joinIds(root, "preparationStyles");
        this.flameLevelIds = joinIds(root, "flameLevels");

        this.actionLabels = buildLabelMap(root, "actions", "displayName");
        this.ingredientLabels = buildLabelMap(root, "ingredients", "name");
        this.preparationStyleLabels = buildLabelMap(root, "preparationStyles", "label");
        this.flameLevelLabels = buildLabelMap(root, "flameLevels", "label");
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

    private Map<String, String> buildLabelMap(JsonNode root, String arrayField, String labelField) {
        Map<String, String> labels = new HashMap<>();
        root.path(arrayField).forEach(entry -> labels.put(entry.path("id").asText(), entry.path(labelField).asText()));
        return labels;
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

    /** Human-readable label for an action id (e.g. {@code "cut"} -> {@code "Cut"}), for AI prompts. */
    public String actionLabel(String id) {
        return actionLabels.getOrDefault(id, id);
    }

    /** Human-readable label for a catalog ingredient id (e.g. {@code "onion"} -> {@code "Onion"}). */
    public String ingredientLabel(String id) {
        return ingredientLabels.getOrDefault(id, id);
    }

    /** Human-readable label for a preparation style id (e.g. {@code "thin-slice"} -> {@code "Thin Slice"}). */
    public String preparationStyleLabel(String id) {
        return preparationStyleLabels.getOrDefault(id, id);
    }

    /** Human-readable label for a flame level id (e.g. {@code "high"} -> {@code "High"}). */
    public String flameLevelLabel(String id) {
        return flameLevelLabels.getOrDefault(id, id);
    }
}
