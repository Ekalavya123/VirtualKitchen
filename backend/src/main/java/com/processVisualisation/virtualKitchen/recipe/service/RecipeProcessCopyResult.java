package com.processVisualisation.virtualKitchen.recipe.service;

import java.util.Map;

/**
 * Outcome of {@link ProcessService#copyAllToRecipe}: which new process id each source
 * process was copied to, which copy is the target recipe's MAIN process (null if the
 * source recipe had no MAIN process), and, per source process id, which new node id each
 * of its nodes was copied to.
 */
public record RecipeProcessCopyResult(Map<Long, Long> processIdMap, Long mainProcessId,
                                      Map<Long, Map<String, String>> nodeIdMaps) {

    public RecipeProcessCopyResult(Map<Long, Long> processIdMap, Long mainProcessId) {
        this(processIdMap, mainProcessId, Map.of());
    }
}
