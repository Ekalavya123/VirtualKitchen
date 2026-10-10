package com.processVisualisation.virtualKitchen.recipeorder;

import com.processVisualisation.virtualKitchen.kitchen.reservation.InventoryReservationStore;
import com.processVisualisation.virtualKitchen.recipe.model.UnitType;
import com.processVisualisation.virtualKitchen.store.model.Inventory;
import com.processVisualisation.virtualKitchen.store.model.ItemType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-memory {@link InventoryReservationStore} with the same guarded, idempotent semantics as the
 * Mongo implementation: each operation is atomic (synchronized) and checks its key and the free
 * stock in the same step. Rows are copied in and out so callers can't mutate stored state.
 */
class InMemoryInventoryReservationStore implements InventoryReservationStore {

    private final Map<Long, Inventory> rows = new LinkedHashMap<>();

    synchronized Inventory addRow(long id, Long kitchenId, Long userId, long ingredientId, double quantity, UnitType unit) {
        Inventory row = new Inventory();
        row.setId(id);
        row.setKitchenId(kitchenId);
        row.setUserId(userId);
        row.setItemType(ItemType.INGREDIENT);
        row.setItemId(ingredientId);
        row.setQuantity(quantity);
        row.setUnit(unit);
        row.setAppliedOps(new ArrayList<>());
        rows.put(id, row);
        return copy(row);
    }

    synchronized Inventory row(long id) {
        return copy(rows.get(id));
    }

    @Override
    public synchronized List<Inventory> findIngredientRows(Long kitchenId, Long userId, Collection<Long> ingredientIds) {
        List<Inventory> result = new ArrayList<>();
        for (Inventory row : rows.values()) {
            boolean inScope = Objects.equals(row.getKitchenId(), kitchenId)
                    || (row.getKitchenId() == null && Objects.equals(row.getUserId(), userId));
            if (inScope && row.getItemType() == ItemType.INGREDIENT && ingredientIds.contains(row.getItemId())) {
                result.add(copy(row));
            }
        }
        return result;
    }

    @Override
    public synchronized Outcome reserve(Long inventoryId, double amount, String reserveKey) {
        Inventory row = rows.get(inventoryId);
        if (row == null) return Outcome.ROW_MISSING;
        if (row.getAppliedOps().contains(reserveKey)) return Outcome.ALREADY_APPLIED;
        if (row.getQuantity() - row.getReservedQuantity() < amount - EPSILON) return Outcome.INSUFFICIENT;
        row.setReservedQuantity(row.getReservedQuantity() + amount);
        row.getAppliedOps().add(reserveKey);
        return Outcome.APPLIED;
    }

    @Override
    public synchronized Outcome release(Long inventoryId, double amount, String reserveKey, String releaseKey, String consumeKey) {
        Inventory row = rows.get(inventoryId);
        if (row == null) return Outcome.ROW_MISSING;
        List<String> ops = row.getAppliedOps();
        if (ops.contains(releaseKey)) return Outcome.ALREADY_APPLIED;
        if (ops.contains(consumeKey)) return Outcome.CLOSED;
        if (!ops.contains(reserveKey)) return Outcome.NOT_RESERVED;
        row.setReservedQuantity(row.getReservedQuantity() - amount);
        ops.add(releaseKey);
        return Outcome.APPLIED;
    }

    @Override
    public synchronized Outcome consume(Long inventoryId, double amount, String reserveKey, String releaseKey, String consumeKey) {
        Inventory row = rows.get(inventoryId);
        if (row == null) return Outcome.ROW_MISSING;
        List<String> ops = row.getAppliedOps();
        if (ops.contains(consumeKey)) return Outcome.ALREADY_APPLIED;
        if (ops.contains(releaseKey)) return Outcome.CLOSED;
        if (!ops.contains(reserveKey)) return Outcome.NOT_RESERVED;
        row.setQuantity(row.getQuantity() - amount);
        row.setReservedQuantity(row.getReservedQuantity() - amount);
        ops.add(consumeKey);
        return Outcome.APPLIED;
    }

    private static Inventory copy(Inventory source) {
        if (source == null) return null;
        Inventory copy = new Inventory();
        copy.setId(source.getId());
        copy.setKitchenId(source.getKitchenId());
        copy.setUserId(source.getUserId());
        copy.setItemType(source.getItemType());
        copy.setItemId(source.getItemId());
        copy.setQuantity(source.getQuantity());
        copy.setUnit(source.getUnit());
        copy.setReservedQuantity(source.getReservedQuantity());
        copy.setAppliedOps(source.getAppliedOps() == null ? null : new ArrayList<>(source.getAppliedOps()));
        return copy;
    }
}
