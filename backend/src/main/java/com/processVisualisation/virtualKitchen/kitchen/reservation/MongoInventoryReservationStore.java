package com.processVisualisation.virtualKitchen.kitchen.reservation;

import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ComparisonOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * {@link InventoryReservationStore} on the {@code inventory} collection: each operation is a
 * single conditional {@code findAndModify} — the same technique {@code CreditService} uses for AI
 * credit reservations — whose filter carries both the business guard (enough free stock) and the
 * idempotency guard (the operation's key not yet applied).
 */
@Repository
public class MongoInventoryReservationStore implements InventoryReservationStore {

    private static final String APPLIED_OPS = "appliedOps";

    private final MongoTemplate mongoTemplate;

    public MongoInventoryReservationStore(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public List<Inventory> findIngredientRows(Long kitchenId, Long userId, Collection<Long> ingredientIds) {
        if (ingredientIds == null || ingredientIds.isEmpty()) return List.of();
        Criteria scope = new Criteria().orOperator(
                where("kitchenId").is(kitchenId),
                where("userId").is(userId).and("kitchenId").is(null));
        Query query = Query.query(new Criteria().andOperator(
                where("itemType").is(ItemType.INGREDIENT),
                where("itemId").in(ingredientIds),
                scope));
        return mongoTemplate.find(query, Inventory.class);
    }

    @Override
    public Outcome reserve(Long inventoryId, double amount, String reserveKey) {
        // quantity - (reservedQuantity ?? 0) >= amount, evaluated by the server in the same atomic update.
        Criteria enoughFree = Criteria.expr(ComparisonOperators.Gte
                .valueOf(ArithmeticOperators.Subtract.valueOf("quantity")
                        .subtract(ConditionalOperators.ifNull("reservedQuantity").then(0)))
                .greaterThanEqualToValue(amount - EPSILON));
        Query query = Query.query(new Criteria().andOperator(
                where("_id").is(inventoryId),
                where(APPLIED_OPS).ne(reserveKey),
                enoughFree));
        Update update = new Update()
                .inc("reservedQuantity", amount)
                .push(APPLIED_OPS, reserveKey)
                .set("lastUpdated", LocalDateTime.now());
        if (modify(query, update)) return Outcome.APPLIED;

        Inventory row = mongoTemplate.findById(inventoryId, Inventory.class);
        if (row == null) return Outcome.ROW_MISSING;
        return applied(row, reserveKey) ? Outcome.ALREADY_APPLIED : Outcome.INSUFFICIENT;
    }

    @Override
    public Outcome release(Long inventoryId, double amount, String reserveKey, String releaseKey, String consumeKey) {
        Update update = new Update()
                .inc("reservedQuantity", -amount)
                .push(APPLIED_OPS, releaseKey)
                .set("lastUpdated", LocalDateTime.now());
        if (modify(openReservation(inventoryId, reserveKey, releaseKey, consumeKey), update)) return Outcome.APPLIED;
        return explainClosed(inventoryId, reserveKey, releaseKey, consumeKey);
    }

    @Override
    public Outcome consume(Long inventoryId, double amount, String reserveKey, String releaseKey, String consumeKey) {
        Update update = new Update()
                .inc("quantity", -amount)
                .inc("reservedQuantity", -amount)
                .push(APPLIED_OPS, consumeKey)
                .set("lastUpdated", LocalDateTime.now());
        if (modify(openReservation(inventoryId, reserveKey, releaseKey, consumeKey), update)) return Outcome.APPLIED;
        return explainClosed(inventoryId, reserveKey, consumeKey, releaseKey);
    }

    /** The row, while it holds {@code reserveKey}'s reservation and that reservation is neither released nor consumed. */
    private static Query openReservation(Long inventoryId, String reserveKey, String releaseKey, String consumeKey) {
        return Query.query(new Criteria().andOperator(
                where("_id").is(inventoryId),
                where(APPLIED_OPS).all(reserveKey).nin(releaseKey, consumeKey)));
    }

    /**
     * Why a release/consume did nothing: the same operation already ran ({@code ownKey} on the row),
     * the reservation was closed the other way ({@code otherKey}), or it was never made.
     */
    private Outcome explainClosed(Long inventoryId, String reserveKey, String ownKey, String otherKey) {
        Inventory row = mongoTemplate.findById(inventoryId, Inventory.class);
        if (row == null) return Outcome.ROW_MISSING;
        if (applied(row, ownKey)) return Outcome.ALREADY_APPLIED;
        if (applied(row, otherKey)) return Outcome.CLOSED;
        if (!applied(row, reserveKey)) return Outcome.NOT_RESERVED;
        return Outcome.CLOSED;
    }

    private boolean modify(Query query, Update update) {
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Inventory.class) != null;
    }

    private static boolean applied(Inventory row, String key) {
        return row.getAppliedOps() != null && row.getAppliedOps().contains(key);
    }
}
