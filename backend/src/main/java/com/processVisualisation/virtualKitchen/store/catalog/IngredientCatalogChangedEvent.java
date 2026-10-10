package com.processVisualisation.virtualKitchen.store.catalog;

/**
 * Published whenever the database ingredient catalog changes (seeding, create, update, delete) so
 * caches built from it — the recipe AI vocabulary and its prompts — reload.
 */
public record IngredientCatalogChangedEvent(String reason) {
}
