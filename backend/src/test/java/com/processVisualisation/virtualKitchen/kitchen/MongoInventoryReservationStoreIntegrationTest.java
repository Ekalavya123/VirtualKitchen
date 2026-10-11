package com.processVisualisation.virtualKitchen.kitchen;

import com.processVisualisation.virtualKitchen.kitchen.reservation.InventoryReservationStore.Outcome;
import com.processVisualisation.virtualKitchen.kitchen.reservation.MongoInventoryReservationStore;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.recipeorder.MongoRecipeOrderStore;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrder;
import com.processVisualisation.virtualKitchen.recipeorder.model.RecipeOrderStatus;
import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.store.model.ItemType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Runs the reservation store's and the order store's guarded updates against the real MongoDB
 * the test context is configured with (like {@code VirtualKitchenApplicationTests}), using
 * throw-away negative ids that are removed afterwards.
 */
@SpringBootTest
class MongoInventoryReservationStoreIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MongoInventoryReservationStore store;

    @Autowired
    private MongoRecipeOrderStore orderStore;

    private final long rowId = -ThreadLocalRandom.current().nextLong(1_000_000_000L, 2_000_000_000L);
    private final long orderId = rowId - 1;

    @AfterEach
    void cleanUp() {
        mongoTemplate.remove(Query.query(where("_id").is(rowId)), Inventory.class);
        mongoTemplate.remove(Query.query(where("_id").is(orderId)), RecipeOrder.class);
    }

    @Test
    void concurrentReservationsNeverHoldMoreThanTheFreeStock() throws Exception {
        insertRow(1.0); // 1 kg; 20 requests of 0.15 kg -> at most 6 fit

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome>> outcomes = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String key = "it:" + rowId + ":" + i + ":reserve";
            outcomes.add(pool.submit(() -> {
                start.await();
                return store.reserve(rowId, 0.15, key);
            }));
        }
        start.countDown();
        int applied = 0;
        for (Future<Outcome> outcome : outcomes) {
            if (outcome.get(60, TimeUnit.SECONDS) == Outcome.APPLIED) applied++;
        }
        pool.shutdown();

        Inventory row = mongoTemplate.findById(rowId, Inventory.class);
        assertEquals(6, applied);
        assertEquals(0.9, row.getReservedQuantity(), 1e-9);
        assertTrue(row.getReservedQuantity() <= row.getQuantity());
    }

    @Test
    void operationsAreIdempotentAndCompletionConsumesOnce() {
        insertRow(1.0);
        String reserve = "it:" + rowId + ":a:reserve", release = "it:" + rowId + ":a:release", consume = "it:" + rowId + ":a:consume";

        assertEquals(Outcome.APPLIED, store.reserve(rowId, 0.4, reserve));
        assertEquals(Outcome.ALREADY_APPLIED, store.reserve(rowId, 0.4, reserve));
        assertEquals(Outcome.APPLIED, store.consume(rowId, 0.4, reserve, release, consume));
        assertEquals(Outcome.ALREADY_APPLIED, store.consume(rowId, 0.4, reserve, release, consume));
        assertEquals(Outcome.CLOSED, store.release(rowId, 0.4, reserve, release, consume));

        Inventory row = mongoTemplate.findById(rowId, Inventory.class);
        assertEquals(0.6, row.getQuantity(), 1e-9);
        assertEquals(0, row.getReservedQuantity(), 1e-9);
        assertEquals(Outcome.NOT_RESERVED, store.release(rowId, 0.4, "never", "never-release", "never-consume"));
        assertEquals(Outcome.INSUFFICIENT, store.reserve(rowId, 0.7, "it:" + rowId + ":b:reserve"));
    }

    @Test
    void releaseGivesStockBackOnce() {
        insertRow(1.0);
        String reserve = "it:" + rowId + ":c:reserve", release = "it:" + rowId + ":c:release", consume = "it:" + rowId + ":c:consume";
        store.reserve(rowId, 1.0, reserve);
        assertEquals(Outcome.APPLIED, store.release(rowId, 1.0, reserve, release, consume));
        assertEquals(Outcome.ALREADY_APPLIED, store.release(rowId, 1.0, reserve, release, consume));
        assertEquals(Outcome.CLOSED, store.consume(rowId, 1.0, reserve, release, consume));
        assertEquals(0, mongoTemplate.findById(rowId, Inventory.class).getReservedQuantity(), 1e-9);
        assertEquals(1.0, mongoTemplate.findById(rowId, Inventory.class).getQuantity(), 1e-9);
    }

    @Test
    void legacyRowsWithoutAReservedFieldCountAsFullyFree() {
        Inventory row = newRow(0.5);
        row.setAppliedOps(null);
        mongoTemplate.insert(row);
        mongoTemplate.updateFirst(Query.query(where("_id").is(rowId)),
                new org.springframework.data.mongodb.core.query.Update().unset("reservedQuantity").unset("appliedOps"), Inventory.class);

        assertEquals(Outcome.APPLIED, store.reserve(rowId, 0.5, "it:" + rowId + ":d:reserve"));
    }

    @Test
    void orderWritesAreCompareAndSet() {
        RecipeOrder order = new RecipeOrder();
        order.setId(orderId);
        order.setUserId(rowId);
        order.setStatus(RecipeOrderStatus.DRAFT);
        orderStore.insert(order);

        RecipeOrder first = orderStore.findById(orderId).orElseThrow();
        RecipeOrder second = orderStore.findById(orderId).orElseThrow();
        first.setStatus(RecipeOrderStatus.AWAITING_INGREDIENTS);
        second.setStatus(RecipeOrderStatus.CANCELLED);

        assertTrue(orderStore.replaceIfVersion(first));
        assertFalse(orderStore.replaceIfVersion(second), "the second writer read a stale version");
        assertEquals(RecipeOrderStatus.AWAITING_INGREDIENTS, orderStore.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, orderStore.findById(orderId).orElseThrow().getVersion());
    }

    private void insertRow(double quantity) {
        mongoTemplate.insert(newRow(quantity));
    }

    private Inventory newRow(double quantity) {
        Inventory row = new Inventory();
        row.setId(rowId);
        row.setKitchenId(rowId);
        row.setUserId(rowId);
        row.setItemType(ItemType.INGREDIENT);
        row.setItemId(rowId);
        row.setQuantity(quantity);
        row.setUnit(UnitType.KG);
        row.setAppliedOps(new ArrayList<>());
        return row;
    }
}
