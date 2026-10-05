package com.processVisualisation.virtualKitchen.ai.workflow;

import com.processVisualisation.virtualKitchen.ai.workflow.model.RecipeAiWorkflow;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RecipeAiWorkflowRepository extends MongoRepository<RecipeAiWorkflow, String> {

    /** The recipe's open workflow, if any (see {@link RecipeAiWorkflow#getActiveKey()}). */
    Optional<RecipeAiWorkflow> findByActiveKey(String activeKey);

    /** The user's newest workflow for the recipe that hasn't been dismissed, offered again when the recipe reopens. */
    Optional<RecipeAiWorkflow> findFirstByRecipeIdAndUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(Long recipeId, Long userId);
}
