package com.processVisualisation.virtualKitchen.recipe.service;

import java.util.Map;

/**
 * Outcome of {@link IProcessService#copyAllToRecipe}: which new process id each source
 * process was copied to, and which copy is the target recipe's MAIN process (null if the
 * source recipe had no MAIN process).
 */
public record RecipeProcessCopyResult(Map<Long, Long> processIdMap, Long mainProcessId) {
}
