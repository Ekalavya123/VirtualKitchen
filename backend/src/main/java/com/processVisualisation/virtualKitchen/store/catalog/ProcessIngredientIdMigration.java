package com.processVisualisation.virtualKitchen.store.catalog;

import com.processVisualisation.virtualKitchen.ai.service.RecipeStepVocabularyProvider;
import com.processVisualisation.virtualKitchen.recipe.model.Process;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Rewrites recipe steps saved under the old static catalog's ingredient slugs ({@code "onion"}) to
 * the database ingredient id ({@code "42"}) — the only id recipe steps, the shop and inventory now
 * share. A slug the catalog doesn't know becomes a custom ingredient that keeps its name.
 * <p>
 * Idempotent: ids that are already numeric (or {@code custom}) are left alone, and only processes
 * that actually change are written (their {@code nodes} field only). Runs after
 * {@link IngredientCatalogSeeder}; disable with {@code vk.migrations.process-ingredient-ids.enabled=false}.
 */
@Component
@Order(2)
public class ProcessIngredientIdMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessIngredientIdMigration.class);

    private final MongoTemplate mongoTemplate;
    private final IngredientRepository ingredientRepository;
    private final boolean enabled;

    public ProcessIngredientIdMigration(
            MongoTemplate mongoTemplate,
            IngredientRepository ingredientRepository,
            @Value("${vk.migrations.process-ingredient-ids.enabled:true}") boolean enabled
    ) {
        this.mongoTemplate = mongoTemplate;
        this.ingredientRepository = ingredientRepository;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        try {
            int migrated = migrate();
            log.info("event=process_ingredient_ids_migrated processes={}", migrated);
        } catch (RuntimeException e) {
            log.error("event=process_ingredient_ids_migration_failed errorType={}", e.getClass().getSimpleName(), e);
        }
    }

    /** @return how many processes were rewritten */
    public int migrate() {
        Map<String, Ingredient> bySlugOrName = new HashMap<>();
        for (Ingredient ingredient : ingredientRepository.findAll()) {
            if (ingredient.getId() == null) continue;
            if (ingredient.getName() != null) bySlugOrName.putIfAbsent(IngredientCatalogSeeder.normalize(ingredient.getName()), ingredient);
        }
        for (Ingredient ingredient : ingredientRepository.findAll()) {
            if (ingredient.getId() != null && ingredient.getCatalogSlug() != null) {
                bySlugOrName.put(IngredientCatalogSeeder.normalize(ingredient.getCatalogSlug()), ingredient);
            }
        }
        Function<String, Ingredient> resolver = slug -> bySlugOrName.get(IngredientCatalogSeeder.normalize(slug));

        int migrated = 0;
        Query withIngredients = Query.query(where("nodes.data.step.actionOn.ingredients.ingredientId").exists(true));
        for (Process process : mongoTemplate.find(withIngredients, Process.class)) {
            if (migrateNodes(process.getNodes(), resolver)) {
                mongoTemplate.updateFirst(Query.query(where("_id").is(process.getId())),
                        new Update().set("nodes", process.getNodes()), Process.class);
                migrated++;
            }
        }
        return migrated;
    }

    /** Rewrites slug ingredient ids in place; returns whether anything changed. */
    @SuppressWarnings("unchecked")
    static boolean migrateNodes(List<Process.ProcessNode> nodes, Function<String, Ingredient> resolver) {
        if (nodes == null) return false;
        boolean changed = false;
        for (Process.ProcessNode node : nodes) {
            if (node == null || node.getData() == null) continue;
            if (!(node.getData().get("step") instanceof Map<?, ?> step)) continue;
            if (!(step.get("actionOn") instanceof Map<?, ?> actionOn)) continue;
            if (!(actionOn.get("ingredients") instanceof List<?> ingredients)) continue;
            for (Object item : ingredients) {
                if (!(item instanceof Map<?, ?> rawEntry)) continue;
                Map<String, Object> entry = (Map<String, Object>) rawEntry;
                if (!(entry.get("ingredientId") instanceof String id) || !needsMigration(id)) continue;
                Ingredient ingredient = resolver.apply(id);
                if (ingredient != null) {
                    entry.put("ingredientId", String.valueOf(ingredient.getId()));
                } else {
                    entry.put("ingredientId", RecipeStepVocabularyProvider.CUSTOM_ID);
                    Object customName = entry.get("customIngredientName");
                    if (!(customName instanceof String name) || name.isBlank()) {
                        entry.put("customIngredientName", labelOf(id));
                    }
                }
                changed = true;
            }
        }
        return changed;
    }

    static boolean needsMigration(String ingredientId) {
        return !ingredientId.isBlank()
                && !RecipeStepVocabularyProvider.CUSTOM_ID.equals(ingredientId)
                && !ingredientId.chars().allMatch(Character::isDigit);
    }

    /** "green-chili" -> "Green Chili". */
    private static String labelOf(String slug) {
        StringBuilder label = new StringBuilder();
        for (String word : slug.split("[-_\\s]+")) {
            if (word.isEmpty()) continue;
            if (!label.isEmpty()) label.append(' ');
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }
}
