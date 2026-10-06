package com.processVisualisation.virtualKitchen.ai.globalasset;

import com.processVisualisation.virtualKitchen.store.model.Equipment;
import com.processVisualisation.virtualKitchen.store.model.Ingredient;
import com.processVisualisation.virtualKitchen.store.repository.EquipmentRepository;
import com.processVisualisation.virtualKitchen.store.repository.IngredientRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * The one place that knows which store catalog backs each {@link GlobalResourceType}: reads a
 * resource's generation-relevant facts and publishes its finished image back onto the catalog
 * entry, where every existing consumer of that catalog ({@code /api/v1/ingredients}, the shop,
 * recipe ingredient lists) already reads {@code imageUrl}.
 */
@Component
public class GlobalResourceResolver {

    private final IngredientRepository ingredientRepository;
    private final EquipmentRepository equipmentRepository;
    private final MongoTemplate mongoTemplate;

    public GlobalResourceResolver(
            IngredientRepository ingredientRepository,
            EquipmentRepository equipmentRepository,
            MongoTemplate mongoTemplate) {
        this.ingredientRepository = ingredientRepository;
        this.equipmentRepository = equipmentRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Optional<GlobalResource> find(GlobalResourceType type, Long id) {
        return switch (type) {
            case INGREDIENT -> ingredientRepository.findById(id).map(GlobalResourceResolver::toResource);
            case EQUIPMENT -> equipmentRepository.findById(id).map(GlobalResourceResolver::toResource);
        };
    }

    /** Every resource of {@code type} whose catalog entry has no image yet. */
    public List<GlobalResource> findMissingImages(GlobalResourceType type) {
        List<GlobalResource> all = switch (type) {
            case INGREDIENT -> ingredientRepository.findAll().stream().map(GlobalResourceResolver::toResource).toList();
            case EQUIPMENT -> equipmentRepository.findAll().stream().map(GlobalResourceResolver::toResource).toList();
        };
        return all.stream().filter(resource -> !StringUtils.hasText(resource.imageUrl())).toList();
    }

    /**
     * Sets the catalog entry's {@code imageUrl} with an in-place update, so a concurrent catalog
     * edit (name, description) is never overwritten by a read-modify-save.
     */
    public void publishImage(GlobalResourceType type, Long id, String imageUrl) {
        Class<?> entity = switch (type) {
            case INGREDIENT -> Ingredient.class;
            case EQUIPMENT -> Equipment.class;
        };
        mongoTemplate.updateFirst(
                query(where("_id").is(id)),
                new Update().set("imageUrl", imageUrl).set("updatedAt", LocalDateTime.now()),
                entity);
    }

    private static GlobalResource toResource(Ingredient ingredient) {
        return new GlobalResource(GlobalResourceType.INGREDIENT, ingredient.getId(), ingredient.getName(),
                ingredient.getDescription(), ingredient.getImageUrl());
    }

    private static GlobalResource toResource(Equipment equipment) {
        return new GlobalResource(GlobalResourceType.EQUIPMENT, equipment.getId(), equipment.getName(),
                equipment.getDescription(), equipment.getImageUrl());
    }
}
