package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Repository
public class MongoRecipeOrderStore implements RecipeOrderStore {

    private final MongoTemplate mongoTemplate;

    public MongoRecipeOrderStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public RecipeOrder insert(RecipeOrder order) {
        return mongoTemplate.insert(order);
    }

    @Override
    public Optional<RecipeOrder> findById(Long id) {
        return Optional.ofNullable(mongoTemplate.findById(id, RecipeOrder.class));
    }

    @Override
    public List<RecipeOrder> findByUserId(Long userId) {
        Query query = Query.query(where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "timestamps.createdAt", "_id"));
        return mongoTemplate.find(query, RecipeOrder.class);
    }

    @Override
    public boolean replaceIfVersion(RecipeOrder order) {
        long expected = order.getVersion();
        order.setVersion(expected + 1);
        RecipeOrder previous = mongoTemplate.findAndReplace(
                Query.query(where("_id").is(order.getId()).and("version").is(expected)), order);
        if (previous == null) {
            order.setVersion(expected);
            return false;
        }
        return true;
    }
}
