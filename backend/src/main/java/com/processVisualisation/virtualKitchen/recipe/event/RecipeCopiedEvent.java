package com.processVisualisation.virtualKitchen.recipe.event;

import java.util.Map;

/**
 * Published by {@code RecipeTemplateService.copyToUser} once a recipe and all its processes have
 * been copied. Features that keep per-recipe data outside the recipe documents (step narration)
 * listen for it to copy that data too, without the recipe module depending on them.
 *
 * @param sourceRecipeId the recipe that was copied
 * @param targetRecipeId the new copy
 * @param userId         the user who owns the copy
 * @param processIdMap   source process id to copied process id
 * @param nodeIdMaps     per source process id: source node (step) id to copied node id
 */
public record RecipeCopiedEvent(Long sourceRecipeId, Long targetRecipeId, Long userId,
                                Map<Long, Long> processIdMap, Map<Long, Map<String, String>> nodeIdMaps) {
}
