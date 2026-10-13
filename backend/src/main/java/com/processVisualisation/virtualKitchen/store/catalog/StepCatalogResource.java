package com.processVisualisation.virtualKitchen.store.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;

/**
 * The shared step catalog file, {@code stepCatalogs.data.json}: the same file the frontend's recipe catalogs are
 * built from, copied onto the classpath at build time (see backend/pom.xml). The ingredient catalog seeder reads
 * its ingredient seed list; the recipe AI vocabulary reads its actions, units and styles.
 */
public final class StepCatalogResource {

    public static final String RESOURCE = "stepCatalogs.data.json";

    /** The id the catalogs use for a user-defined ("custom") entry instead of a catalog one. */
    public static final String CUSTOM_ID = "custom";

    private StepCatalogResource() {
    }

    /** The parsed catalog file. */
    public static JsonNode read() {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            return new ObjectMapper().readTree(stream);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load " + RESOURCE + " from the classpath", e);
        }
    }
}
